/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TamingFetch extends SimpleAdaptation<TamingFetch.Config> {
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

  private final Map<UUID, FetchJob> activeFetches = new ConcurrentHashMap<>();
  private final Set<UUID> reservedItems = ConcurrentHashMap.newKeySet();
  private final AtomicLong fetchSequence = new AtomicLong();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean(false);

  public TamingFetch() {
    super("tame-fetch");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.fetch");
    setIcon(Id.minecraft("hopper"));
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("hopper"))
        .key("challenge_taming_fetch_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chest"))
            .key("challenge_taming_fetch_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_fetch_1k", "taming.fetch.items-fetched", 1000, 400);
    registerMilestone("challenge_taming_fetch_10k", "taming.fetch.items-fetched", 10000, 1500);
  }

  @Override
  public void unregister() {
    if (!lifecycleCleanupStarted.compareAndSet(false, true)) {
      super.unregister();
      return;
    }

    super.unregister();
    FetchJob[] jobs = activeFetches.values().toArray(new FetchJob[0]);
    for (FetchJob job : jobs) {
      abortFetch(job);
    }
    activeFetches.clear();
    reservedItems.clear();
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    abortOwnerFetches(player.getPlayer().uuid());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.ITEMS, TamingPreferences.COLLAR);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getFetchRange(level), 1), 1);
    statLore(v, Form.pc(getCarryRate(level), 0), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    abortOwnerFetches(e.player().uuid());
  }

  @Override
  public void onTick() {
    if (!isEnabled()) {
      return;
    }

    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      PlayerRef owner = adaptPlayer.getPlayer();
      if (owner == null) {
        continue;
      }
      Tasks.runEntity(owner, () -> fetchForOwner(owner));
    }
  }

  private void fetchForOwner(PlayerRef owner) {
    if (lifecycleCleanupStarted.get() || !owner.isOnline()) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    UUID ownerId = owner.uuid();
    List<MobRef> available = new ArrayList<>(getWolfLimit());
    int availableWolfCount = collectFetchWolves(owner, ownerId, available);
    if (availableWolfCount <= 0) {
      return;
    }

    Loc origin = owner.location();
    if (origin.world() == null) {
      return;
    }

    double range = getFetchRange(level);
    double carryRate = getCarryRate(level);
    int maxThisTick = Math.min(availableWolfCount, getCarryPerTickLimit());
    FetchPass pass = new FetchPass(owner, ownerId, origin, available,
        pathfindRadius(getConfig().pathfindRadius),
        claimRadius(getConfig().pathfindRadius, range),
        fetchWalkSpeed(getConfig().fetchWalkSpeed),
        System.currentTimeMillis() + fetchDeadlineMillis(getConfig().fetchDeadlineMillis));

    int dispatched = 0;
    for (ItemEntityRef item : nearbyItems(origin, range)) {
      if (dispatched >= maxThisTick) {
        break;
      }
      if (reservedItems.contains(item.uuid()) || !isFetchable(item, ownerId)
          || !preference(owner, TamingPreferences.ITEMS).accepts(item.item().type())) {
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

  private int collectFetchWolves(PlayerRef owner, UUID ownerId, List<MobRef> available) {
    int limit = getWolfLimit();
    double radius = Math.max(1.0D, getConfig().wolfSearchRadius);
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(owner.location(), radius, radius)) {
      return 0;
    }
    for (EntityRef entity : owner.nearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof MobRef wolf) || !WOLF.equals(wolf.type()) || !wolf.isValid() || wolf.isDead()
          || !animals().isTamed(wolf) || !isOwnedBy(wolf, ownerId)) {
        continue;
      }

      if (preference(owner, TamingPreferences.COLLAR).accepts(animals().collarColor(wolf))
          && isFetchWolfEligible(wolf, ownerId) && !activeFetches.containsKey(wolf.uuid())) {
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
    if (lifecycleCleanupStarted.get() || !isFetchWolfEligible(job.wolf, job.ownerId)) {
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
    if (lifecycleCleanupStarted.get()
        || !Tasks.runEntity(job.wolf, () -> maintainFetchOwned(job), getMaintenanceIntervalTicks())) {
      abortFetch(job);
    }
  }

  private void maintainFetchOwned(FetchJob job) {
    if (!isCurrentJob(job)) {
      return;
    }
    if (lifecycleCleanupStarted.get() || !isFetchWolfEligible(job.wolf, job.ownerId)) {
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
    if (!owner.isOnline() || getActiveLevel(owner) <= 0) {
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
    fx(itemLocation, FxPriority.GAMEPLAY)
        .burst(Particles.VILLAGER_HAPPY, 6, 0.25D)
        .sound(Sounds.ENTITY_ITEM_PICKUP, 0.5F, 1.4F);

    if (!Tasks.runEntity(job.wolf, () -> steerWolfHomeOwned(job))) {
      abortFetch(job);
    }
  }

  private void steerWolfHomeOwned(FetchJob job) {
    if (!isCurrentJob(job)) {
      return;
    }

    Loc ownerLocation = job.ownerLocation;
    if (lifecycleCleanupStarted.get() || ownerLocation == null || !isFetchWolfEligible(job.wolf, job.ownerId)
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
    Loc ownerLocation = owner.isOnline() && getActiveLevel(owner) > 0 ? owner.location() : null;
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
    if (lifecycleCleanupStarted.get() || !isFetchWolfEligible(job.wolf, job.ownerId)
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
    fx(from, FxPriority.GAMEPLAY).sound(Sounds.ENTITY_WOLF_PANT, 0.7F, 1.25F);
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
    addStat(owner, "taming.fetch.items-fetched", 1);
    xp(owner, getConfig().xpPerItemFetched);
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

  private void abortOwnerFetches(UUID ownerId) {
    for (FetchJob job : new ArrayList<>(activeFetches.values())) {
      if (ownerId.equals(job.ownerId)) {
        abortFetch(job);
      }
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

  private double getFetchRange(int level) {
    return getConfig().fetchRangeBase + (getLevelPercent(level) * getConfig().fetchRangeFactor);
  }

  private double getCarryRate(int level) {
    return Math.min(getConfig().maxCarryRate, getConfig().carryRateBase + (getLevelPercent(level) * getConfig().carryRateFactor));
  }

  private int getWolfLimit() {
    return Math.max(1, Math.min(getConfig().maxWolves, HARD_MAX_WOLVES));
  }

  private int getCarryPerTickLimit() {
    return Math.max(1, Math.min(getConfig().maxCarryPerTick, HARD_MAX_CARRY_PER_TICK));
  }

  private int getMaintenanceIntervalTicks() {
    return maintenanceIntervalTicks(getConfig().maintenanceIntervalTicks);
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

  @ConfigDescription("Your idle tamed wolves physically run to nearby dropped items, pick them up, and carry them back.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fetch Range Base for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fetchRangeBase = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fetch Range Factor for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fetchRangeFactor = 10.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Carry Rate Base for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double carryRateBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Carry Rate Factor for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double carryRateFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Carry Rate for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxCarryRate = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wolf Search Radius for the Taming Fetch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double wolfSearchRadius = 24.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time a wolf fetches a dropped item.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemFetched = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum tamed wolves counted around the owner, capped internally at 12.", impact = "Higher values let larger packs fetch more items per pass but scan more entities.")
    int maxWolves = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum dropped items fetched per owner each pass, capped internally at 8.", impact = "Lower values reduce item scheduling in dense drops.")
    int maxCarryPerTick = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Movement speed multiplier used while a wolf walks a fetch, clamped to 0.1 - 4.0.", impact = "Higher values make the round trip finish sooner but look more frantic.")
    double fetchWalkSpeed = 1.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum physical fetch distance in blocks, clamped internally at 11 by vanilla pet follow behavior.", impact = "Higher values let wolves claim more distant drops; drops beyond it remain where they are.")
    double pathfindRadius = 9.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds a walked fetch may run before it is given up, clamped to 1000 - 60000.", impact = "Higher values let wolves keep working awkward routes instead of releasing the drop.")
    long fetchDeadlineMillis = 9000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks between re-issuing a walking wolf its path, clamped to 1 - 20.", impact = "Lower values hold the route tighter against vanilla goals but schedule more often.")
    int maintenanceIntervalTicks = 5;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      initialCost = 1;
    }
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

    private FetchPass(PlayerRef owner, UUID ownerId, Loc origin, List<MobRef> wolves,
                      double pathRadius, double claimRadius, double walkSpeed, long deadlineMillis) {
      this.owner = owner;
      this.ownerId = ownerId;
      this.origin = origin;
      this.wolves = wolves;
      this.pathRadius = pathRadius;
      this.claimRadius = claimRadius;
      this.walkSpeed = walkSpeed;
      this.deadlineMillis = deadlineMillis;
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
      this.generation = generation;
      this.ownerLocation = pass.origin;
    }
  }
}
