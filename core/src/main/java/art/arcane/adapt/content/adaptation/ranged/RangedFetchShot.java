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

package art.arcane.adapt.content.adaptation.ranged;

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
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RangedFetchShot extends SimpleAdaptation<RangedFetchShot.Config> {
  private static final DataKey<Byte> LAUNCH_SNEAK_KEY = DataKey.adapt("fetch-launch-sneak", DataCodecs.BYTE);
  private static final Id FISHING_BOBBER = Id.minecraft("fishing_bobber");
  private static final int PLAYER_STORAGE_SLOTS = 36;
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 8;

  public RangedFetchShot() {
    super("ranged-fetch-shot");
    ProjectileCooperativeKeys.register(LAUNCH_SNEAK_KEY.id());
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fishing_rod"));
    setInterval(2751);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("hopper"))
        .key("challenge_ranged_fetch_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chest"))
            .key("challenge_ranged_fetch_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_fetch_500", "ranged.fetch-shot.items-fetched", 500, 400);
    registerMilestone("challenge_ranged_fetch_5k", "ranged.fetch-shot.items-fetched", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK, RangedPreferences.ITEMS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent event) {
    if (!(event.projectile().shooter() instanceof PlayerRef player)
        || !Tasks.isOwnedByCurrentRegion(player)
        || !hasActiveAdaptation(player)) {
      return;
    }
    event.projectile().data().set(LAUNCH_SNEAK_KEY, player.isSneaking() ? (byte) 1 : (byte) 0);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || projectile.type().equals(FISHING_BOBBER)
        || !(projectile.shooter() instanceof PlayerRef p)) {
      return;
    }

    Loc impact = projectile.location();
    Byte sneak = projectile.data().get(LAUNCH_SNEAK_KEY);
    boolean sneakingAtLaunch = sneak != null && sneak == 1;
    Tasks.runEntity(p, () -> startFetchOwned(p, impact, sneakingAtLaunch));
  }

  private void startFetchOwned(PlayerRef player, Loc impact, boolean sneakingAtLaunch) {
    if (!player.isOnline()) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0 || (preferenceEnabled(player, RangedPreferences.SNEAK) && !sneakingAtLaunch)) {
      return;
    }

    if (!canAccessChest(player, impact)) {
      return;
    }

    double radius = getRadius(level);
    FetchBatch batch = new FetchBatch(player, impact, radius, getAffectedLimit(), getTargetFxLimit(),
        getConfig().xpPerItemFetched);
    Tasks.runAt(impact, () -> scanImpactOwned(batch));
  }

  private void scanImpactOwned(FetchBatch batch) {
    int limit = getCandidateLimit();
    List<ItemEntityRef> candidates = new ArrayList<>(limit);
    for (EntityRef entity : batch.impact.world().entitiesIn(
        Box.around(batch.impact.position(), batch.radius, batch.radius, batch.radius),
        (EntityRef candidate) -> candidate instanceof ItemEntityRef)) {
      if (!(entity instanceof ItemEntityRef item)) {
        continue;
      }
      candidates.add(item);
      if (candidates.size() >= limit) {
        break;
      }
    }

    if (candidates.isEmpty()) {
      return;
    }
    batch.remaining.set(candidates.size());
    for (ItemEntityRef item : candidates) {
      if (!Tasks.runEntity(item, () -> reserveItemOwned(batch, item))) {
        batch.complete(false);
      }
    }
    Tasks.runEntity(batch.player, batch::finishTimedOut, 5);
  }

  private void reserveItemOwned(FetchBatch batch, ItemEntityRef item) {
    if (batch.isFinalized()) {
      return;
    }
    Loc itemLocation = validItemLocation(batch, item);
    if (itemLocation == null || !batch.hasAffectedCapacity() || !canSnatchItem(batch.player, item)) {
      batch.complete(false);
      return;
    }

    ItemRef stack = item.item().copy();
    if (!Tasks.runEntity(batch.player, () -> prepareTransferOwned(batch, item, itemLocation, stack))) {
      batch.complete(false);
    }
  }

  private void prepareTransferOwned(FetchBatch batch, ItemEntityRef item, Loc itemLocation, ItemRef stack) {
    if (batch.isFinalized() || !batch.player.isOnline() || getActiveLevel(batch.player) <= 0
        || !preference(batch.player, RangedPreferences.ITEMS).accepts(stack.type())) {
      batch.complete(false);
      return;
    }

    int transferAmount = getInventoryCapacity(batch.player, stack);
    if (transferAmount <= 0) {
      batch.complete(false);
      return;
    }

    int remaining = Math.max(0, stack.amount() - transferAmount);
    if (!Tasks.isOwnedByCurrentRegion(item)
        || !ProtectionProbes.attemptItemPickup(batch.player, item, remaining)
        || validItemLocation(batch, item) == null
        || !batch.claimAffected()) {
      batch.complete(false);
      return;
    }

    ItemRef transfer = stack.copyWithAmount(transferAmount);
    if (!Tasks.runEntity(item, () -> consumeItemOwned(batch, item, itemLocation, stack, transfer))) {
      batch.complete(false);
    }
  }

  private void consumeItemOwned(FetchBatch batch, ItemEntityRef item, Loc itemLocation, ItemRef snapshot,
                                ItemRef transfer) {
    if (batch.isFinalized() || validItemLocation(batch, item) == null) {
      batch.complete(false);
      return;
    }

    ItemRef current = item.item();
    if (!current.isSimilar(snapshot) || current.amount() < transfer.amount()) {
      batch.complete(false);
      return;
    }

    int remaining = current.amount() - transfer.amount();
    boolean itemRemoved = remaining <= 0;
    if (itemRemoved) {
      item.remove();
    } else {
      item.setItem(current.copyWithAmount(remaining));
    }

    TransferState transferState = new TransferState();
    if (!Tasks.runEntity(batch.player, () -> deliverItemOwned(batch, itemLocation, transfer, transferState))) {
      if (itemRemoved) {
        dropRestoredItem(itemLocation, transfer);
      } else {
        restoreConsumedItemOwned(item, itemLocation, transfer);
      }
      batch.complete(false);
      return;
    }
    Tasks.runAt(itemLocation, () -> expireTransferOwned(itemLocation, transfer, transferState), 6);
  }

  private Loc validItemLocation(FetchBatch batch, ItemEntityRef item) {
    if (!item.isValid() || item.isInvulnerable() || item.pickupDelay() >= Short.MAX_VALUE) {
      return null;
    }
    UUID owner = item.owner();
    EntityMarkerPort markers = AdaptPorts.get(EntityMarkerPort.class, EntityMarkerPort.NONE);
    if ((owner != null && !owner.equals(batch.playerId))
        || markers.marked(item, "NPC")
        || markers.marked(item, "shopitem")
        || markers.marked(item, "hologram")) {
      return null;
    }
    if (!item.canPlayerPickup()) {
      return null;
    }

    Loc location = item.location();
    if (!location.sameWorld(batch.impact)
        || Math.abs(location.x() - batch.impact.x()) > batch.radius
        || Math.abs(location.y() - batch.impact.y()) > batch.radius
        || Math.abs(location.z() - batch.impact.z()) > batch.radius) {
      return null;
    }
    return location;
  }

  private void deliverItemOwned(FetchBatch batch, Loc itemLocation, ItemRef stack,
                                TransferState transferState) {
    if (!transferState.resolve()) {
      return;
    }
    if (batch.isFinalized()) {
      dropRestoredItem(itemLocation, stack);
      return;
    }
    if (!batch.player.isOnline()) {
      dropRestoredItem(itemLocation, stack);
      batch.complete(false);
      return;
    }

    int requested = stack.amount();
    List<ItemRef> leftovers = batch.player.inventory().addItem(stack.copy());
    int remaining = 0;
    for (ItemRef leftover : leftovers) {
      if (leftover != null) {
        remaining += leftover.amount();
      }
    }

    int inserted = Math.max(0, requested - remaining);
    if (remaining > 0) {
      dropRestoredItem(itemLocation, stack.copyWithAmount(remaining));
    }
    if (inserted <= 0) {
      batch.complete(false);
      return;
    }

    if (batch.claimTargetFx() && itemLocation.world().equals(batch.player.world())) {
      Loc eye = batch.player.eyeLocation();
      fx(itemLocation, FxPriority.TRAIL)
          .line(Particles.ENCHANTMENT_TABLE, eye.x(), eye.y(), eye.z(), 5)
          .chord(Sounds.ENTITY_ITEM_PICKUP, 0.6F, 1.5F, Sounds.ENTITY_ARROW_HIT_PLAYER, 0.3F, 1.8F);
    }
    batch.complete(true);
  }

  private void expireTransferOwned(Loc itemLocation, ItemRef stack, TransferState transferState) {
    if (transferState.resolve() && itemLocation.world() != null) {
      itemLocation.world().dropItem(itemLocation.position(), stack);
    }
  }

  private int getInventoryCapacity(PlayerRef player, ItemRef stack) {
    int requested = stack.amount();
    int capacity = 0;
    int maxStackSize = stack.maxStackSize();
    PlayerInventoryRef inventory = player.inventory();
    int slots = Math.min(PLAYER_STORAGE_SLOTS, inventory.size());
    for (int index = 0; index < slots; index++) {
      ItemRef slot = inventory.item(index);
      if (slot == null || slot.isEmpty()) {
        capacity += maxStackSize;
      } else if (slot.isSimilar(stack)) {
        capacity += Math.max(0, maxStackSize - slot.amount());
      }
      if (capacity >= requested) {
        return requested;
      }
    }
    return Math.min(requested, capacity);
  }

  private void restoreConsumedItemOwned(ItemEntityRef item, Loc location, ItemRef stack) {
    if (item.isValid()) {
      ItemRef current = item.item();
      if (current.isSimilar(stack) && current.amount() + stack.amount() <= current.maxStackSize()) {
        item.setItem(current.copyWithAmount(current.amount() + stack.amount()));
        return;
      }
    }
    dropRestoredItem(location, stack);
  }

  private void dropRestoredItem(Loc location, ItemRef stack) {
    if (location.world() != null && !stack.isEmpty() && stack.amount() > 0) {
      Tasks.runAt(location, () -> location.world().dropItem(location.position(), stack));
    }
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit()), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Shoot dropped items with projectiles to pull them straight into your inventory.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Item Fetched for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemFetched = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum dropped-item candidates inspected by one projectile impact.", impact = "Lower values reduce dense-item scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum dropped-item entities transferred by one projectile impact.", impact = "Lower values cap inventory and entity work in item piles; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum successful fetches that receive individual trail effects.", impact = "Lower values reduce particle and sound dispatch; values are clamped to an absolute maximum of 8.")
    int maxTargetFxPerActivation = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      maxLevel = 3;
      initialCost = 4;
    }
  }

  private final class FetchBatch {
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc impact;
    private final double radius;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerItemFetched;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger targetFx = new AtomicInteger();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private FetchBatch(PlayerRef player, Loc impact, double radius, int affectedLimit, int targetFxLimit,
                       double xpPerItemFetched) {
      this.player = player;
      playerId = player.uuid();
      this.impact = impact;
      this.radius = radius;
      this.affectedLimit = affectedLimit;
      this.targetFxLimit = targetFxLimit;
      this.xpPerItemFetched = xpPerItemFetched;
      remaining = new AtomicInteger();
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < affectedLimit;
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || affected.get() >= affectedLimit) {
        return false;
      }
      affected.incrementAndGet();
      return true;
    }

    private boolean claimTargetFx() {
      return claim(targetFx, targetFxLimit);
    }

    private boolean claim(AtomicInteger counter, int limit) {
      int current = counter.get();
      while (current < limit) {
        if (counter.compareAndSet(current, current + 1)) {
          return true;
        }
        current = counter.get();
      }
      return false;
    }

    private void complete(boolean success) {
      if (finalized.get()) {
        return;
      }
      if (success) {
        succeeded.incrementAndGet();
      }
      if (remaining.decrementAndGet() != 0 || !markFinalized()) {
        return;
      }
      Tasks.runEntity(player, this::finish);
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finish();
      }
    }

    private void finish() {
      int fetched = succeeded.get();
      if (!player.isOnline() || fetched <= 0) {
        return;
      }
      addStat(player, "ranged.fetch-shot.items-fetched", fetched);
      xp(player, fetched * xpPerItemFetched);
    }
  }

  private static final class TransferState {
    private final AtomicBoolean resolved = new AtomicBoolean();

    private boolean resolve() {
      return resolved.compareAndSet(false, true);
    }
  }
}
