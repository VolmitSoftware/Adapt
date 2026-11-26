package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Tamed wolves physically run to dropped items near their owner, pick them up and carry them back. */
public final class TameFetcher {
  private static final int HARD_MAX_WOLVES = 12;
  private static final int HARD_MAX_CARRY_PER_TICK = 8;
  // Vanilla TamableAnimal yanks the pet back to its owner at 12 blocks, which kills any plugin path.
  private static final double MAX_PATHFIND_RADIUS = 11.0D;
  private static final double WOLF_LEASH_LIMIT_SQUARED = MAX_PATHFIND_RADIUS * MAX_PATHFIND_RADIUS;
  private static final double PICKUP_DISTANCE_SQUARED = 2.25D;
  private static final double DELIVER_DISTANCE_SQUARED = 4.0D;
  private static final double MIN_WALK_SPEED = 0.1D;
  private static final double MAX_WALK_SPEED = 4.0D;
  private static final double DEFAULT_WALK_SPEED = 1.0D;
  private static final int MIN_MAINTENANCE_TICKS = 1;
  private static final int MAX_MAINTENANCE_TICKS = 20;
  private static final long MIN_DEADLINE_MILLIS = 1000L;
  private static final long MAX_DEADLINE_MILLIS = 60000L;
  private static final Id WOLF = Id.minecraft("wolf");

  private final Adaptation<?> adaptation;
  private final Consumer<PlayerRef> onFetched;
  private final Map<UUID, FetchJob> activeFetches = new ConcurrentHashMap<>();
  private final Set<UUID> reservedItems = ConcurrentHashMap.newKeySet();
  private final AtomicLong fetchSequence = new AtomicLong();
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /** {@code onFetched} runs on the owner's thread for every item a wolf delivers. */
  public TameFetcher(Adaptation<?> adaptation, Consumer<PlayerRef> onFetched) {
    this.adaptation = adaptation;
    this.onFetched = onFetched;
  }

  public record Tuning(double range, double carryRate, double wolfSearchRadius, int maxWolves, int maxCarryPerTick,
                       double pathfindRadius, double walkSpeed, long deadlineMillis, int maintenanceTicks) {
  }

  /** Aborts every fetch and returns carried items; later calls do nothing. */
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    FetchJob[] jobs = activeFetches.values().toArray(new FetchJob[0]);
    for (FetchJob job : jobs) {
      abortFetch(job);
    }
    activeFetches.clear();
    reservedItems.clear();
  }

  public void abortOwnerFetches(UUID ownerId) {
    for (FetchJob job : new ArrayList<>(activeFetches.values())) {
      if (ownerId.equals(job.ownerId)) {
        abortFetch(job);
      }
    }
  }

  public static int wolfLimit(int configured) {
    return Math.max(1, Math.min(configured, HARD_MAX_WOLVES));
  }

  public static int carryPerTickLimit(int configured) {
    return Math.max(1, Math.min(configured, HARD_MAX_CARRY_PER_TICK));
  }

  /** Sends idle wolves after the drops around the owner; returns how many fetches started. */
  public int fetch(PlayerRef owner, IntFunction<Tuning> tuningForLevel) {
    return fetch(owner, tuningForLevel, (MobRef wolf) -> true);
  }

  /** {@link #fetch(PlayerRef, IntFunction)} with only the wolves {@code eligible} accepts. */
  public int fetch(PlayerRef owner, IntFunction<Tuning> tuningForLevel, Predicate<MobRef> eligible) {
    if (closed.get() || !owner.isOnline()) {
      return 0;
    }

    int level = adaptation.getActiveLevel(owner);
    if (level <= 0) {
      return 0;
    }

    Tuning tuning = tuningForLevel.apply(level);

    UUID ownerId = owner.uuid();
    List<MobRef> available = new ArrayList<>(wolfLimit(tuning.maxWolves()));
    int availableWolfCount = collectFetchWolves(owner, ownerId, available, tuning, eligible);
    if (availableWolfCount <= 0) {
      return 0;
    }

    Loc origin = owner.location();
    if (origin.world() == null) {
      return 0;
    }

    double range = tuning.range();
    double carryRate = tuning.carryRate();
    int maxThisTick = Math.min(availableWolfCount, carryPerTickLimit(tuning.maxCarryPerTick()));
    FetchPass pass = new FetchPass(owner, ownerId, origin, available,
        pathfindRadius(tuning.pathfindRadius()),
        claimRadius(tuning.pathfindRadius(), range),
        fetchWalkSpeed(tuning.walkSpeed()),
        System.currentTimeMillis() + fetchDeadlineMillis(tuning.deadlineMillis()),
        maintenanceIntervalTicks(tuning.maintenanceTicks()));

    int dispatched = 0;
    for (ItemEntityRef item : nearbyItems(origin, range)) {
      if (dispatched >= maxThisTick) {
        break;
      }
      if (reservedItems.contains(item.uuid()) || !isFetchable(item, ownerId)
          || !adaptation.preference(owner, TamingPreferences.ITEMS).accepts(item.item().type())) {
        continue;
      }
      if (Math.random() > carryRate) {
        continue;
      }

      Loc itemLocation = item.location();
      if (!withinClaimRadius(pass, itemLocation)) {
        continue;
      }
      if (pass.wolves.isEmpty()) {
        break;
      }
      if (startFetch(pass, item, itemLocation)) {
        dispatched++;
      }
    }
    return dispatched;
  }

  private List<ItemEntityRef> nearbyItems(Loc origin, double range) {
    List<ItemEntityRef> items = new ArrayList<>();
    WorldRef world = origin.world();
    if (world == null || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(origin, range, range))) {
      return items;
    }
    for (EntityRef entity : world.entitiesIn(Box.around(origin.position(), range, range, range),
        candidate -> candidate instanceof ItemEntityRef)) {
      items.add((ItemEntityRef) entity);
    }
    return items;
  }

  private int collectFetchWolves(PlayerRef owner, UUID ownerId, List<MobRef> available, Tuning tuning,
                                 Predicate<MobRef> eligible) {
    int limit = wolfLimit(tuning.maxWolves());
    double radius = Math.max(1.0D, tuning.wolfSearchRadius());
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(owner.location(), radius, radius)) {
      return 0;
    }
    for (EntityRef entity : owner.nearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof MobRef wolf) || !WOLF.equals(wolf.type()) || !wolf.isValid() || wolf.isDead()
          || !animals().isTamed(wolf) || !isOwnedBy(wolf, ownerId)) {
        continue;
      }

      if (adaptation.preference(owner, TamingPreferences.COLLAR).accepts(animals().collarColor(wolf))
          && isFetchWolfEligible(wolf, ownerId) && eligible.test(wolf) && !activeFetches.containsKey(wolf.uuid())) {
        available.add(wolf);
      }
      if (available.size() >= limit) {
        break;
      }
    }
    return available.size();
  }

  private boolean withinClaimRadius(FetchPass pass, Loc itemLocation) {
    return itemLocation.sameWorld(pass.origin)
        && itemLocation.distanceSquared(pass.origin) <= pass.claimRadius * pass.claimRadius;
  }

  private boolean startFetch(FetchPass pass, ItemEntityRef item, Loc itemLocation) {
    MobRef wolf = nearestAvailableWolf(pass, itemLocation);
    if (wolf == null) {
      return false;
    }

    UUID itemId = item.uuid();
    if (!reservedItems.add(itemId)) {
      return false;
    }

    FetchJob job = new FetchJob(pass, wolf, itemId, item, fetchSequence.incrementAndGet());
    if (activeFetches.putIfAbsent(job.wolfId, job) != null) {
      reservedItems.remove(itemId);
      return false;
    }

    pass.wolves.remove(wolf);
    if (!Tasks.runEntity(wolf, () -> beginOutboundOwned(job, itemLocation))) {
      abortFetch(job);
      return false;
    }
    return true;
  }

  private MobRef nearestAvailableWolf(FetchPass pass, Loc itemLocation) {
    MobRef nearest = null;
    double nearestDistanceSquared = pass.pathRadius * pass.pathRadius;
    for (MobRef wolf : pass.wolves) {
      Loc wolfLocation = wolf.location();
      if (!wolfLocation.sameWorld(itemLocation)) {
        continue;
      }
      double distanceSquared = wolfLocation.distanceSquared(itemLocation);
      if (distanceSquared <= nearestDistanceSquared) {
        nearestDistanceSquared = distanceSquared;
        nearest = wolf;
      }
    }
    return nearest;
  }

  private void beginOutboundOwned(FetchJob job, Loc itemLocation) {
    if (!isCurrentJob(job)) {
      return;
    }
    if (closed.get() || !isFetchWolfEligible(job.wolf, job.ownerId)) {
      abortFetch(job);
      return;
    }

    job.wolfLocation = job.wolf.location();
    if (!job.wolf.pathTo(itemLocation, job.walkSpeed)) {
      abortFetch(job);
      return;
    }
    scheduleMaintenance(job);
  }

  private void scheduleMaintenance(FetchJob job) {
    if (closed.get()
        || !Tasks.runEntity(job.wolf, () -> maintainFetchOwned(job), job.maintenanceTicks)) {
      abortFetch(job);
    }
  }

  private void maintainFetchOwned(FetchJob job) {
    if (!isCurrentJob(job)) {
      return;
    }
    if (closed.get() || !isFetchWolfEligible(job.wolf, job.ownerId)) {
      abortFetch(job);
      return;
    }

    Loc wolfLocation = job.wolf.location();
    job.wolfLocation = wolfLocation;
    boolean scheduled = job.phase == FetchPhase.OUTBOUND
        ? Tasks.runEntity(job.item, () -> approachItemOwned(job, wolfLocation))
        : Tasks.runEntity(job.owner, () -> approachOwnerOwned(job, wolfLocation));
    if (!scheduled) {
      abortFetch(job);
    }
  }

  private void approachItemOwned(FetchJob job, Loc wolfLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    ItemEntityRef item = job.item;
    boolean reachable = item.isValid() && !item.isDead() && isFetchable(item, job.ownerId)
        && item.location().sameWorld(wolfLocation);
    Loc itemLocation = reachable ? item.location() : null;
    double distanceSquared = reachable ? itemLocation.distanceSquared(wolfLocation) : Double.MAX_VALUE;
    FetchStep step = nextFetchStep(System.currentTimeMillis(), job.deadlineMillis, reachable,
        distanceSquared, PICKUP_DISTANCE_SQUARED);
    if (step == FetchStep.ABORT) {
      abortFetch(job);
      return;
    }
    if (step == FetchStep.ARRIVE) {
      pickUpItemOwned(job, itemLocation);
      return;
    }
    if (!Tasks.runEntity(job.owner, () -> trackOwnerOwned(job, wolfLocation, itemLocation))) {
      abortFetch(job);
    }
  }

  private void trackOwnerOwned(FetchJob job, Loc wolfLocation, Loc itemLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    PlayerRef owner = job.owner;
    if (!owner.isOnline() || adaptation.getActiveLevel(owner) <= 0) {
      abortFetch(job);
      return;
    }

    Loc ownerLocation = owner.location();
    job.ownerLocation = ownerLocation;
    if (!ownerLocation.sameWorld(wolfLocation)
        || ownerLocation.distanceSquared(wolfLocation) > WOLF_LEASH_LIMIT_SQUARED) {
      abortFetch(job);
      return;
    }
    if (!Tasks.runEntity(job.wolf, () -> steerWolfOwned(job, itemLocation))) {
      abortFetch(job);
    }
  }

  private void pickUpItemOwned(FetchJob job, Loc itemLocation) {
    ItemEntityRef item = job.item;
    ItemRef stack = item.item();
    if (!isCurrentJob(job)
        || !Tasks.isOwnedByCurrentRegion(job.owner)
        || stack == null
        || stack.isEmpty()
        || stack.amount() <= 0
        || !ProtectionProbes.attemptItemPickup(job.owner, item, 0)
        || !item.isValid()
        || item.isDead()) {
      abortFetch(job);
      return;
    }

    ItemRef current = item.item();
    if (!current.isSimilar(stack) || current.amount() != stack.amount()) {
      abortFetch(job);
      return;
    }

    ItemRef carried = current.copy();
    item.remove();
    reservedItems.remove(job.itemId);
    job.carried.set(carried);
    job.phase = FetchPhase.INBOUND;
    TamingFetch.pickupFx(adaptation, itemLocation);

    if (!Tasks.runEntity(job.wolf, () -> steerWolfHomeOwned(job))) {
      abortFetch(job);
    }
  }

  private void steerWolfHomeOwned(FetchJob job) {
    if (!isCurrentJob(job)) {
      return;
    }

    Loc ownerLocation = job.ownerLocation;
    if (closed.get() || ownerLocation == null || !isFetchWolfEligible(job.wolf, job.ownerId)
        || !job.wolf.pathTo(ownerLocation, job.walkSpeed)) {
      abortFetch(job);
      return;
    }
    scheduleMaintenance(job);
  }

  private void approachOwnerOwned(FetchJob job, Loc wolfLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    PlayerRef owner = job.owner;
    Loc ownerLocation = owner.isOnline() && adaptation.getActiveLevel(owner) > 0 ? owner.location() : null;
    if (ownerLocation != null) {
      job.ownerLocation = ownerLocation;
    }

    boolean sameWorld = ownerLocation != null && ownerLocation.sameWorld(wolfLocation);
    double distanceSquared = sameWorld ? ownerLocation.distanceSquared(wolfLocation) : Double.MAX_VALUE;
    FetchStep step = nextFetchStep(System.currentTimeMillis(), job.deadlineMillis,
        sameWorld && distanceSquared <= WOLF_LEASH_LIMIT_SQUARED, distanceSquared, DELIVER_DISTANCE_SQUARED);
    if (step == FetchStep.ABORT) {
      abortFetch(job);
      return;
    }

    boolean scheduled = step == FetchStep.ARRIVE
        ? Tasks.runEntity(job.wolf, () -> releaseCarriedOwned(job, ownerLocation))
        : Tasks.runEntity(job.wolf, () -> steerWolfOwned(job, ownerLocation));
    if (!scheduled) {
      abortFetch(job);
    }
  }

  private void steerWolfOwned(FetchJob job, Loc target) {
    if (!isCurrentJob(job)) {
      return;
    }
    if (closed.get() || !isFetchWolfEligible(job.wolf, job.ownerId)
        || !job.wolf.pathTo(target, job.walkSpeed)) {
      abortFetch(job);
      return;
    }
    scheduleMaintenance(job);
  }

  private void releaseCarriedOwned(FetchJob job, Loc ownerLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    ItemRef carried = job.carried.getAndSet(null);
    releaseJob(job);
    if (carried == null) {
      return;
    }

    MobRef wolf = job.wolf;
    if (!wolf.isValid() || wolf.isDead() || !wolf.location().sameWorld(ownerLocation)) {
      returnCarried(job, carried);
      return;
    }

    wolf.stopPathing();
    Loc from = wolf.location();
    TamingFetch.deliverFx(adaptation, from);
    ItemEntityRef delivered = wolf.world().dropItem(from.position(), carried);
    if (delivered != null) {
      delivered.setPickupDelay(0);
      delivered.setOwner(job.ownerId);
    }
    if (!Tasks.runEntity(job.owner, () -> creditFetch(job.owner))) {
      AdaptLog.warn("Taming Fetch delivered an item but could not schedule credit for "
          + job.ownerId + ".");
    }
  }

  private void creditFetch(PlayerRef owner) {
    if (!owner.isOnline()) {
      return;
    }
    onFetched.accept(owner);
  }

  private boolean isCurrentJob(FetchJob job) {
    FetchJob current = activeFetches.get(job.wolfId);
    return current != null && current.generation == job.generation;
  }

  private void releaseJob(FetchJob job) {
    activeFetches.remove(job.wolfId, job);
    reservedItems.remove(job.itemId);
  }

  private void abortFetch(FetchJob job) {
    releaseJob(job);
    ItemRef carried = job.carried.getAndSet(null);
    if (!Tasks.runEntity(job.wolf, () -> releaseWolfOwned(job, carried)) && carried != null) {
      returnCarried(job, carried);
    }
  }

  private void releaseWolfOwned(FetchJob job, ItemRef carried) {
    MobRef wolf = job.wolf;
    if (!wolf.isValid() || wolf.isDead()) {
      if (carried != null) {
        returnCarried(job, carried);
      }
      return;
    }

    FetchJob current = activeFetches.get(job.wolfId);
    Long currentGeneration = current == null ? null : current.generation;
    if (shouldStopPathfinding(job.generation, currentGeneration)) {
      wolf.stopPathing();
    }
    if (carried != null) {
      dropCarriedOwned(wolf.location(), job.ownerId, carried);
    }
  }

  private void returnCarried(FetchJob job, ItemRef carried) {
    Loc fallback = job.ownerLocation != null ? job.ownerLocation : job.wolfLocation;
    if (fallback == null || fallback.world() == null) {
      AdaptLog.warn("Taming Fetch lost the drop location for a carried stack of " + job.ownerId + ".");
      return;
    }
    if (canActDirectly(fallback)) {
      dropCarriedOwned(fallback, job.ownerId, carried);
      return;
    }
    if (!Tasks.runAt(fallback, () -> dropCarriedOwned(fallback, job.ownerId, carried))) {
      AdaptLog.warn("Taming Fetch could not return a carried stack to " + job.ownerId + ".");
    }
  }

  private void dropCarriedOwned(Loc where, UUID ownerId, ItemRef carried) {
    WorldRef world = where.world();
    if (world == null) {
      AdaptLog.warn("Taming Fetch could not return a carried stack to " + ownerId + ".");
      return;
    }

    ItemEntityRef dropped = world.dropItem(where.position(), carried);
    if (dropped != null) {
      dropped.setPickupDelay(0);
      dropped.setOwner(ownerId);
    }
  }

  private boolean canActDirectly(Loc location) {
    return Tasks.isFoliaThreading() ? Tasks.isOwnedByCurrentRegion(location) : Tasks.isPrimaryThread();
  }

  private boolean isFetchable(ItemEntityRef item, UUID ownerId) {
    if (item == null || !item.isValid() || item.isDead() || item.pickupDelay() >= Short.MAX_VALUE) {
      return false;
    }
    UUID owner = item.owner();
    if (owner != null && !owner.equals(ownerId)) {
      return false;
    }
    EntityMarkerPort markers = AdaptPorts.get(EntityMarkerPort.class, EntityMarkerPort.NONE);
    if (markers.marked(item, "NPC") || markers.marked(item, "shopitem") || markers.marked(item, "hologram")) {
      return false;
    }
    return item.canPlayerPickup();
  }

  private boolean isFetchWolfEligible(MobRef wolf, UUID ownerId) {
    return isFetchWolfEligible(
        animals().isTamed(wolf) && isOwnedBy(wolf, ownerId),
        wolf.isValid() && !wolf.isDead(),
        animals().isSitting(wolf),
        animals().isLeashed(wolf),
        wolf.vehicle() != null || !wolf.passengers().isEmpty());
  }

  private boolean isOwnedBy(EntityRef tameable, UUID ownerId) {
    UUID owner = tameable.tameOwner();
    return owner != null && ownerId.equals(owner);
  }

  private static AnimalOps animals() {
    return AdaptCore.platform().host().animals();
  }

  static boolean isFetchWolfEligible(boolean tamedByOwner, boolean alive, boolean sitting, boolean leashed,
                                     boolean mounted) {
    return tamedByOwner && alive && !sitting && !leashed && !mounted;
  }

  static FetchStep nextFetchStep(long nowMillis, long deadlineMillis, boolean contextValid, double distanceSquared,
                                 double arriveDistanceSquared) {
    if (!contextValid) {
      return FetchStep.ABORT;
    }
    if (distanceSquared <= arriveDistanceSquared) {
      return FetchStep.ARRIVE;
    }
    return nowMillis >= deadlineMillis ? FetchStep.ABORT : FetchStep.CONTINUE;
  }

  static double pathfindRadius(double configuredRadius) {
    if (!Double.isFinite(configuredRadius)) {
      return 0.0D;
    }
    return Math.max(0.0D, Math.min(configuredRadius, MAX_PATHFIND_RADIUS));
  }

  static double claimRadius(double configuredRadius, double fetchRange) {
    double range = Double.isFinite(fetchRange) ? Math.max(0.0D, fetchRange) : 0.0D;
    return Math.min(pathfindRadius(configuredRadius), range);
  }

  static double fetchWalkSpeed(double configuredSpeed) {
    if (!Double.isFinite(configuredSpeed)) {
      return DEFAULT_WALK_SPEED;
    }
    return Math.max(MIN_WALK_SPEED, Math.min(configuredSpeed, MAX_WALK_SPEED));
  }

  static int maintenanceIntervalTicks(int configuredTicks) {
    return Math.max(MIN_MAINTENANCE_TICKS, Math.min(configuredTicks, MAX_MAINTENANCE_TICKS));
  }

  static long fetchDeadlineMillis(long configuredMillis) {
    return Math.max(MIN_DEADLINE_MILLIS, Math.min(configuredMillis, MAX_DEADLINE_MILLIS));
  }

  static boolean shouldStopPathfinding(long cleanupGeneration, Long currentGeneration) {
    return currentGeneration == null || currentGeneration == cleanupGeneration;
  }

  enum FetchStep {
    CONTINUE,
    ARRIVE,
    ABORT
  }

  private enum FetchPhase {
    OUTBOUND,
    INBOUND
  }

  private static final class FetchPass {
    private final PlayerRef owner;
    private final UUID ownerId;
    private final Loc origin;
    private final List<MobRef> wolves;
    private final double pathRadius;
    private final double claimRadius;
    private final double walkSpeed;
    private final long deadlineMillis;
    private final int maintenanceTicks;

    private FetchPass(PlayerRef owner, UUID ownerId, Loc origin, List<MobRef> wolves,
                      double pathRadius, double claimRadius, double walkSpeed, long deadlineMillis,
                      int maintenanceTicks) {
      this.owner = owner;
      this.ownerId = ownerId;
      this.origin = origin;
      this.wolves = wolves;
      this.pathRadius = pathRadius;
      this.claimRadius = claimRadius;
      this.walkSpeed = walkSpeed;
      this.deadlineMillis = deadlineMillis;
      this.maintenanceTicks = maintenanceTicks;
    }
  }

  private static final class FetchJob {
    private final UUID wolfId;
    private final MobRef wolf;
    private final UUID itemId;
    private final ItemEntityRef item;
    private final PlayerRef owner;
    private final UUID ownerId;
    private final double walkSpeed;
    private final long deadlineMillis;
    private final int maintenanceTicks;
    private final long generation;
    private final AtomicReference<ItemRef> carried = new AtomicReference<>();
    private volatile FetchPhase phase = FetchPhase.OUTBOUND;
    private volatile Loc ownerLocation;
    private volatile Loc wolfLocation;

    private FetchJob(FetchPass pass, MobRef wolf, UUID itemId, ItemEntityRef item, long generation) {
      this.wolfId = wolf.uuid();
      this.wolf = wolf;
      this.itemId = itemId;
      this.item = item;
      this.owner = pass.owner;
      this.ownerId = pass.ownerId;
      this.walkSpeed = pass.walkSpeed;
      this.deadlineMillis = pass.deadlineMillis;
      this.maintenanceTicks = pass.maintenanceTicks;
      this.generation = generation;
      this.ownerLocation = pass.origin;
    }
  }
}
