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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

  private final Map<UUID, FetchJob> activeFetches = new ConcurrentHashMap<>();
  private final Set<UUID> reservedItems = ConcurrentHashMap.newKeySet();
  private final AtomicLong fetchSequence = new AtomicLong();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean(false);

  public TamingFetch() {
    super("tame-fetch");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.fetch");
    setIcon(Material.HOPPER);
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HOPPER)
        .key("challenge_taming_fetch_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHEST)
            .key("challenge_taming_fetch_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
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
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getFetchRange(level), 1), 1);
    statLore(v, Form.pc(getCarryRate(level), 0), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    abortOwnerFetches(e.getPlayer().getUniqueId());
  }

  @Override
  public void onTick() {
    if (!isEnabled()) {
      return;
    }

    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player owner = adaptPlayer.getPlayer();
      if (owner == null) {
        continue;
      }
      J.runEntity(owner, () -> fetchForOwner(owner));
    }
  }

  private void fetchForOwner(Player owner) {
    if (lifecycleCleanupStarted.get() || !owner.isOnline()) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    UUID ownerId = owner.getUniqueId();
    List<Wolf> available = new ArrayList<>(getWolfLimit());
    int wolfCount = collectFetchWolves(owner, ownerId, available);
    if (wolfCount <= 0) {
      return;
    }

    Location origin = owner.getLocation();
    if (origin.getWorld() == null) {
      return;
    }

    double range = getFetchRange(level);
    double carryRate = getCarryRate(level);
    int maxThisTick = Math.min(wolfCount, getCarryPerTickLimit());
    FetchPass pass = new FetchPass(owner, ownerId, origin.clone(), available,
        getConfig().realFetch && PaperCompat.supportsPathfinding(),
        pathfindRadius(getConfig().pathfindRadius),
        claimRadius(getConfig().pathfindRadius, range),
        fetchWalkSpeed(getConfig().fetchWalkSpeed),
        System.currentTimeMillis() + fetchDeadlineMillis(getConfig().fetchDeadlineMillis));

    int dispatched = 0;
    for (Item item : PaperCompat.nearbyEntitiesByType(Item.class, origin, range, range, range)) {
      if (dispatched >= maxThisTick) {
        break;
      }
      if (reservedItems.contains(item.getUniqueId()) || !isFetchable(item, ownerId)) {
        continue;
      }
      if (Math.random() > carryRate) {
        continue;
      }

      Location itemLocation = item.getLocation().clone();
      if (pass.pathfinding && withinClaimRadius(pass, itemLocation)) {
        if (pass.wolves.isEmpty()) {
          break;
        }
        if (startRealFetch(pass, item, itemLocation)) {
          dispatched++;
        }
        continue;
      }

      dispatched++;
      Location deliverTo = pass.origin.clone();
      J.runEntity(item, () -> deliverItem(owner, ownerId, deliverTo, item));
    }
  }

  private int collectFetchWolves(Player owner, UUID ownerId, List<Wolf> available) {
    int limit = getWolfLimit();
    double radius = Math.max(1.0D, getConfig().wolfSearchRadius);
    int count = 0;
    for (Entity entity : owner.getNearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof Wolf wolf) || !wolf.isValid() || wolf.isDead() || !wolf.isTamed()
          || !isOwnedBy(wolf, ownerId)) {
        continue;
      }

      count++;
      if (isFetchWolfEligible(wolf, ownerId) && !activeFetches.containsKey(wolf.getUniqueId())) {
        available.add(wolf);
      }
      if (count >= limit) {
        break;
      }
    }
    return count;
  }

  private boolean withinClaimRadius(FetchPass pass, Location itemLocation) {
    return itemLocation.getWorld() == pass.origin.getWorld()
        && itemLocation.distanceSquared(pass.origin) <= pass.claimRadius * pass.claimRadius;
  }

  private boolean startRealFetch(FetchPass pass, Item item, Location itemLocation) {
    Wolf wolf = nearestAvailableWolf(pass, itemLocation);
    if (wolf == null) {
      return false;
    }

    UUID itemId = item.getUniqueId();
    if (!reservedItems.add(itemId)) {
      return false;
    }

    FetchJob job = new FetchJob(pass, wolf, itemId, item, fetchSequence.incrementAndGet());
    if (activeFetches.putIfAbsent(job.wolfId, job) != null) {
      reservedItems.remove(itemId);
      return false;
    }

    pass.wolves.remove(wolf);
    if (!J.runEntity(wolf, () -> beginOutboundOwned(job, itemLocation))) {
      abortFetch(job);
      return false;
    }
    return true;
  }

  private Wolf nearestAvailableWolf(FetchPass pass, Location itemLocation) {
    Wolf nearest = null;
    double nearestDistanceSquared = pass.pathRadius * pass.pathRadius;
    for (Wolf wolf : pass.wolves) {
      Location wolfLocation = wolf.getLocation();
      if (wolfLocation.getWorld() != itemLocation.getWorld()) {
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

  private void beginOutboundOwned(FetchJob job, Location itemLocation) {
    if (!isCurrentJob(job)) {
      return;
    }
    if (lifecycleCleanupStarted.get() || !isFetchWolfEligible(job.wolf, job.ownerId)) {
      abortFetch(job);
      return;
    }

    job.wolfLocation = job.wolf.getLocation().clone();
    if (!PaperCompat.pathfindTo(job.wolf, itemLocation, job.walkSpeed)) {
      fallBackToTeleport(job);
      return;
    }
    scheduleMaintenance(job);
  }

  private void scheduleMaintenance(FetchJob job) {
    if (lifecycleCleanupStarted.get()
        || !J.runEntity(job.wolf, () -> maintainFetchOwned(job), getMaintenanceIntervalTicks())) {
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

    Location wolfLocation = job.wolf.getLocation().clone();
    job.wolfLocation = wolfLocation;
    boolean scheduled = job.phase == FetchPhase.OUTBOUND
        ? J.runEntity(job.item, () -> approachItemOwned(job, wolfLocation))
        : J.runEntity(job.owner, () -> approachOwnerOwned(job, wolfLocation));
    if (!scheduled) {
      abortFetch(job);
    }
  }

  private void approachItemOwned(FetchJob job, Location wolfLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    Item item = job.item;
    boolean reachable = item.isValid() && !item.isDead() && isFetchable(item, job.ownerId)
        && item.getWorld() == wolfLocation.getWorld();
    Location itemLocation = reachable ? item.getLocation().clone() : null;
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
    if (!J.runEntity(job.owner, () -> trackOwnerOwned(job, wolfLocation, itemLocation))) {
      abortFetch(job);
    }
  }

  private void trackOwnerOwned(FetchJob job, Location wolfLocation, Location itemLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    Player owner = job.owner;
    if (!owner.isOnline() || getActiveLevel(owner) <= 0) {
      abortFetch(job);
      return;
    }

    Location ownerLocation = owner.getLocation().clone();
    job.ownerLocation = ownerLocation;
    if (ownerLocation.getWorld() != wolfLocation.getWorld()
        || ownerLocation.distanceSquared(wolfLocation) > WOLF_LEASH_LIMIT_SQUARED) {
      abortFetch(job);
      return;
    }
    if (!J.runEntity(job.wolf, () -> steerWolfOwned(job, itemLocation))) {
      abortFetch(job);
    }
  }

  private void pickUpItemOwned(FetchJob job, Location itemLocation) {
    Item item = job.item;
    ItemStack stack = item.getItemStack();
    if (!isCurrentJob(job) || stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
      abortFetch(job);
      return;
    }

    ItemStack carried = stack.clone();
    item.remove();
    reservedItems.remove(job.itemId);
    job.carried.set(carried);
    job.phase = FetchPhase.INBOUND;
    fx(itemLocation, FxPriority.GAMEPLAY)
        .burst(Particles.VILLAGER_HAPPY, 6, 0.25D)
        .sound(Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.4F);

    if (!J.runEntity(job.wolf, () -> steerWolfHomeOwned(job))) {
      abortFetch(job);
    }
  }

  private void steerWolfHomeOwned(FetchJob job) {
    if (!isCurrentJob(job)) {
      return;
    }

    Location ownerLocation = job.ownerLocation;
    if (lifecycleCleanupStarted.get() || ownerLocation == null || !isFetchWolfEligible(job.wolf, job.ownerId)
        || !PaperCompat.pathfindTo(job.wolf, ownerLocation, job.walkSpeed)) {
      abortFetch(job);
      return;
    }
    scheduleMaintenance(job);
  }

  private void approachOwnerOwned(FetchJob job, Location wolfLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    Player owner = job.owner;
    Location ownerLocation = owner.isOnline() && getActiveLevel(owner) > 0 ? owner.getLocation().clone() : null;
    if (ownerLocation != null) {
      job.ownerLocation = ownerLocation;
    }

    boolean sameWorld = ownerLocation != null && ownerLocation.getWorld() == wolfLocation.getWorld();
    double distanceSquared = sameWorld ? ownerLocation.distanceSquared(wolfLocation) : Double.MAX_VALUE;
    FetchStep step = nextFetchStep(System.currentTimeMillis(), job.deadlineMillis,
        sameWorld && distanceSquared <= WOLF_LEASH_LIMIT_SQUARED, distanceSquared, DELIVER_DISTANCE_SQUARED);
    if (step == FetchStep.ABORT) {
      abortFetch(job);
      return;
    }

    boolean scheduled = step == FetchStep.ARRIVE
        ? J.runEntity(job.wolf, () -> releaseCarriedOwned(job, ownerLocation))
        : J.runEntity(job.wolf, () -> steerWolfOwned(job, ownerLocation));
    if (!scheduled) {
      abortFetch(job);
    }
  }

  private void steerWolfOwned(FetchJob job, Location target) {
    if (!isCurrentJob(job)) {
      return;
    }
    if (lifecycleCleanupStarted.get() || !isFetchWolfEligible(job.wolf, job.ownerId)
        || !PaperCompat.pathfindTo(job.wolf, target, job.walkSpeed)) {
      abortFetch(job);
      return;
    }
    scheduleMaintenance(job);
  }

  private void releaseCarriedOwned(FetchJob job, Location ownerLocation) {
    if (!isCurrentJob(job)) {
      return;
    }

    ItemStack carried = job.carried.getAndSet(null);
    releaseJob(job);
    if (carried == null) {
      return;
    }

    Wolf wolf = job.wolf;
    if (!wolf.isValid() || wolf.isDead() || wolf.getWorld() != ownerLocation.getWorld()) {
      returnCarried(job, carried);
      return;
    }

    PaperCompat.stopPathfinding(wolf);
    Location from = wolf.getLocation().clone();
    fx(from, FxPriority.GAMEPLAY).sound(Sound.ENTITY_WOLF_PANT, 0.7F, 1.25F);
    Item delivered = wolf.getWorld().dropItem(from, carried);
    Player owner = job.owner;
    UUID ownerId = job.ownerId;
    Location deliverTo = ownerLocation.clone();
    if (!J.runEntity(delivered, () -> deliverItem(owner, ownerId, deliverTo, delivered))) {
      delivered.setPickupDelay(0);
      delivered.setOwner(ownerId);
    }
  }

  private void deliverItem(Player owner, UUID ownerId, Location ownerLocation, Item item) {
    if (!isFetchable(item, ownerId)) {
      return;
    }

    Location from = item.getLocation().clone();
    Location to = ownerLocation.clone().add(0, 0.5, 0);
    beginFetchTeleport(owner, ownerId, item, from, to);
  }

  private void beginFetchTeleport(Player owner, UUID ownerId, Item item, Location from, Location to) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = PaperCompat.teleportAsync(item, to);
    } catch (RuntimeException error) {
      Adapt.error("Taming Fetch could not start item teleport for " + item.getUniqueId() + ".");
      error.printStackTrace();
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeFetchTeleport(owner, ownerId, item, from, to, success, failure));
  }

  private void completeFetchTeleport(Player owner, UUID ownerId, Item item, Location from, Location to,
                                     Boolean success, Throwable failure) {
    if (failure != null) {
      Adapt.error("Taming Fetch item teleport failed for " + item.getUniqueId() + ".");
      failure.printStackTrace();
    }
    if (!successfulFetchTeleport(success, failure)) {
      return;
    }
    if (!J.runEntity(item, () -> finishFetchTeleportOwned(owner, ownerId, item, from, to))) {
      Adapt.warn("Taming Fetch completed item teleport but could not schedule completion for "
          + item.getUniqueId() + ".");
    }
  }

  private void finishFetchTeleportOwned(Player owner, UUID ownerId, Item item, Location from, Location to) {
    if (!item.isValid() || item.isDead()) {
      return;
    }

    item.setPickupDelay(0);
    item.setOwner(ownerId);
    if (from.getWorld() != null && from.getWorld() == to.getWorld()) {
      fx(from, FxPriority.TRAIL)
          .line(Particles.ENCHANTMENT_TABLE, to.getX(), to.getY(), to.getZ(), 6)
          .sound(Sound.ENTITY_ITEM_PICKUP, 0.45F, 1.7F);
    }

    if (!J.runEntity(owner, () -> creditFetch(owner))) {
      Adapt.warn("Taming Fetch delivered an item but could not schedule credit for "
          + owner.getUniqueId() + ".");
    }
  }

  static boolean successfulFetchTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private void creditFetch(Player owner) {
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
    ItemStack carried = job.carried.getAndSet(null);
    if (!J.runEntity(job.wolf, () -> releaseWolfOwned(job, carried)) && carried != null) {
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

  private void fallBackToTeleport(FetchJob job) {
    Item item = job.item;
    Player owner = job.owner;
    UUID ownerId = job.ownerId;
    Location deliverTo = job.ownerLocation;
    releaseJob(job);
    PaperCompat.stopPathfinding(job.wolf);
    if (deliverTo != null) {
      J.runEntity(item, () -> deliverItem(owner, ownerId, deliverTo, item));
    }
  }

  private void releaseWolfOwned(FetchJob job, ItemStack carried) {
    Wolf wolf = job.wolf;
    if (!wolf.isValid() || wolf.isDead()) {
      if (carried != null) {
        returnCarried(job, carried);
      }
      return;
    }

    PaperCompat.stopPathfinding(wolf);
    if (carried != null) {
      dropCarriedOwned(wolf.getLocation(), job.ownerId, carried);
    }
  }

  private void returnCarried(FetchJob job, ItemStack carried) {
    Location fallback = job.ownerLocation != null ? job.ownerLocation : job.wolfLocation;
    if (fallback == null || fallback.getWorld() == null) {
      Adapt.warn("Taming Fetch lost the drop location for a carried stack of " + job.ownerId + ".");
      return;
    }
    if (canActDirectly(fallback)) {
      dropCarriedOwned(fallback, job.ownerId, carried);
      return;
    }
    if (!J.runAt(fallback, () -> dropCarriedOwned(fallback, job.ownerId, carried))) {
      Adapt.warn("Taming Fetch could not return a carried stack to " + job.ownerId + ".");
    }
  }

  private void dropCarriedOwned(Location where, UUID ownerId, ItemStack carried) {
    World world = where.getWorld();
    if (world == null) {
      Adapt.warn("Taming Fetch could not return a carried stack to " + ownerId + ".");
      return;
    }

    Item dropped = world.dropItem(where, carried);
    dropped.setPickupDelay(0);
    dropped.setOwner(ownerId);
  }

  private boolean canActDirectly(Location location) {
    return J.isFoliaThreading() ? J.isOwnedByCurrentRegion(location) : J.isPrimaryThread();
  }

  private boolean isFetchable(Item item, UUID ownerId) {
    if (item == null || !item.isValid() || item.isDead() || item.getPickupDelay() >= Short.MAX_VALUE) {
      return false;
    }
    UUID owner = item.getOwner();
    if (owner != null && !owner.equals(ownerId)) {
      return false;
    }
    if (item.hasMetadata("NPC") || item.hasMetadata("shopitem") || item.hasMetadata("hologram")) {
      return false;
    }
    return PaperCompat.canPlayerPickup(item);
  }

  private boolean isFetchWolfEligible(Wolf wolf, UUID ownerId) {
    return isFetchWolfEligible(
        wolf.isTamed() && isOwnedBy(wolf, ownerId),
        wolf.isValid() && !wolf.isDead(),
        wolf.isSitting(),
        wolf.isLeashed(),
        wolf.getVehicle() != null || !wolf.getPassengers().isEmpty());
  }

  private boolean isOwnedBy(Tameable tameable, UUID ownerId) {
    AnimalTamer owner = tameable.getOwner();
    return owner != null && ownerId.equals(owner.getUniqueId());
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

  @ConfigDescription("Your tamed wolves gather nearby dropped items and bring them straight to you.")
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Sends a wolf walking to the drop and back instead of pulling the item straight to you.", impact = "False restores the instant pull for every drop; true only walks drops inside the pathfind radius.")
    boolean realFetch = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Movement speed multiplier used while a wolf walks a fetch, clamped to 0.1 - 4.0.", impact = "Higher values make the round trip finish sooner but look more frantic.")
    double fetchWalkSpeed = 1.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum walked fetch distance in blocks, clamped internally at 11 by vanilla pet follow behavior.", impact = "Higher values walk more drops; drops beyond it are pulled to you instead.")
    double pathfindRadius = 9.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds a walked fetch may run before it is given up, clamped to 1000 - 60000.", impact = "Higher values let wolves keep working awkward routes instead of releasing the drop.")
    long fetchDeadlineMillis = 9000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks between re-issuing a walking wolf its path, clamped to 1 - 20.", impact = "Lower values hold the route tighter against vanilla goals but schedule more often.")
    int maintenanceIntervalTicks = 5;

    public Config() {
      baseCost = 3;
      costFactor = 0.4;
      initialCost = 3;
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
    private final Player owner;
    private final UUID ownerId;
    private final Location origin;
    private final List<Wolf> wolves;
    private final boolean pathfinding;
    private final double pathRadius;
    private final double claimRadius;
    private final double walkSpeed;
    private final long deadlineMillis;

    private FetchPass(Player owner, UUID ownerId, Location origin, List<Wolf> wolves, boolean pathfinding,
                      double pathRadius, double claimRadius, double walkSpeed, long deadlineMillis) {
      this.owner = owner;
      this.ownerId = ownerId;
      this.origin = origin;
      this.wolves = wolves;
      this.pathfinding = pathfinding;
      this.pathRadius = pathRadius;
      this.claimRadius = claimRadius;
      this.walkSpeed = walkSpeed;
      this.deadlineMillis = deadlineMillis;
    }
  }

  private static final class FetchJob {
    private final UUID wolfId;
    private final Wolf wolf;
    private final UUID itemId;
    private final Item item;
    private final Player owner;
    private final UUID ownerId;
    private final double walkSpeed;
    private final long deadlineMillis;
    private final long generation;
    private final AtomicReference<ItemStack> carried = new AtomicReference<>();
    private volatile FetchPhase phase = FetchPhase.OUTBOUND;
    private volatile Location ownerLocation;
    private volatile Location wolfLocation;

    private FetchJob(FetchPass pass, Wolf wolf, UUID itemId, Item item, long generation) {
      this.wolfId = wolf.getUniqueId();
      this.wolf = wolf;
      this.itemId = itemId;
      this.item = item;
      this.owner = pass.owner;
      this.ownerId = pass.ownerId;
      this.walkSpeed = pass.walkSpeed;
      this.deadlineMillis = pass.deadlineMillis;
      this.generation = generation;
      this.ownerLocation = pass.origin.clone();
    }
  }
}
