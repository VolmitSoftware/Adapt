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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RangedFetchShot extends SimpleAdaptation<RangedFetchShot.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 8;

  public RangedFetchShot() {
    super("ranged-fetch-shot");
    registerConfiguration(Config.class);
    setIcon(Material.FISHING_ROD);
    setInterval(2751);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HOPPER)
        .key("challenge_ranged_fetch_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHEST)
            .key("challenge_ranged_fetch_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_fetch_500", "ranged.fetch-shot.items-fetched", 500, 400);
    registerMilestone("challenge_ranged_fetch_5k", "ranged.fetch-shot.items-fetched", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    Projectile projectile = e.getEntity();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || projectile instanceof FishHook
        || !(projectile.getShooter() instanceof Player p)) {
      return;
    }

    Location impact = projectile.getLocation().clone();
    J.runEntity(p, () -> startFetchOwned(p, impact));
  }

  private void startFetchOwned(Player player, Location impact) {
    if (!player.isOnline()) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    if (!canAccessChest(player, impact)) {
      return;
    }

    double radius = getRadius(level);
    FetchBatch batch = new FetchBatch(player, impact, radius, getAffectedLimit(), getTargetFxLimit(),
        getConfig().xpPerItemFetched);
    J.runAt(impact, () -> scanImpactOwned(batch));
  }

  private void scanImpactOwned(FetchBatch batch) {
    int limit = getCandidateLimit();
    List<Item> candidates = new ArrayList<>(limit);
    for (Item item : PaperCompat.nearbyEntitiesByType(
        Item.class, batch.impact, batch.radius, batch.radius, batch.radius)) {
      candidates.add(item);
      if (candidates.size() >= limit) {
        break;
      }
    }

    if (candidates.isEmpty()) {
      return;
    }
    batch.remaining.set(candidates.size());
    for (Item item : candidates) {
      if (!J.runEntity(item, () -> reserveItemOwned(batch, item))) {
        batch.complete(false);
      }
    }
    J.runEntity(batch.player, batch::finishTimedOut, 5);
  }

  private void reserveItemOwned(FetchBatch batch, Item item) {
    if (batch.isFinalized()) {
      return;
    }
    Location itemLocation = validItemLocation(batch, item);
    if (itemLocation == null || !batch.hasAffectedCapacity() || !canSnatchItem(batch.player, item)) {
      batch.complete(false);
      return;
    }

    ItemStack stack = item.getItemStack().clone();
    if (!J.runEntity(batch.player, () -> prepareTransferOwned(batch, item, itemLocation, stack))) {
      batch.complete(false);
    }
  }

  private void prepareTransferOwned(FetchBatch batch, Item item, Location itemLocation, ItemStack stack) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete(false);
      return;
    }

    int transferAmount = getInventoryCapacity(batch.player, stack);
    if (transferAmount <= 0) {
      batch.complete(false);
      return;
    }

    int remaining = Math.max(0, stack.getAmount() - transferAmount);
    if (!J.isOwnedByCurrentRegion(item)
        || !ProtectionEventProbe.attemptItemPickup(batch.player, item, remaining)
        || validItemLocation(batch, item) == null
        || !batch.claimAffected()) {
      batch.complete(false);
      return;
    }

    ItemStack transfer = stack.clone();
    transfer.setAmount(transferAmount);
    if (!J.runEntity(item, () -> consumeItemOwned(batch, item, itemLocation, stack, transfer))) {
      batch.complete(false);
    }
  }

  private void consumeItemOwned(FetchBatch batch, Item item, Location itemLocation, ItemStack snapshot,
                                ItemStack transfer) {
    if (batch.isFinalized() || validItemLocation(batch, item) == null) {
      batch.complete(false);
      return;
    }

    ItemStack current = item.getItemStack();
    if (!current.isSimilar(snapshot) || current.getAmount() < transfer.getAmount()) {
      batch.complete(false);
      return;
    }

    int remaining = current.getAmount() - transfer.getAmount();
    boolean itemRemoved = remaining <= 0;
    if (itemRemoved) {
      item.remove();
    } else {
      ItemStack reduced = current.clone();
      reduced.setAmount(remaining);
      item.setItemStack(reduced);
    }

    TransferState transferState = new TransferState();
    if (!J.runEntity(batch.player, () -> deliverItemOwned(batch, itemLocation, transfer, transferState))) {
      if (itemRemoved) {
        dropRestoredItem(itemLocation, transfer);
      } else {
        restoreConsumedItemOwned(item, itemLocation, transfer);
      }
      batch.complete(false);
      return;
    }
    J.runAt(itemLocation, () -> expireTransferOwned(itemLocation, transfer, transferState), 6);
  }

  private Location validItemLocation(FetchBatch batch, Item item) {
    if (!item.isValid() || item.isInvulnerable() || item.getPickupDelay() >= Short.MAX_VALUE) {
      return null;
    }
    UUID owner = item.getOwner();
    if ((owner != null && !owner.equals(batch.playerId))
        || item.hasMetadata("NPC")
        || item.hasMetadata("shopitem")
        || item.hasMetadata("hologram")) {
      return null;
    }
    if (!PaperCompat.canPlayerPickup(item)) {
      return null;
    }

    Location location = item.getLocation();
    if (location.getWorld() != batch.impact.getWorld()
        || Math.abs(location.getX() - batch.impact.getX()) > batch.radius
        || Math.abs(location.getY() - batch.impact.getY()) > batch.radius
        || Math.abs(location.getZ() - batch.impact.getZ()) > batch.radius) {
      return null;
    }
    return location;
  }

  private void deliverItemOwned(FetchBatch batch, Location itemLocation, ItemStack stack,
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

    int requested = stack.getAmount();
    Map<Integer, ItemStack> leftovers = batch.player.getInventory().addItem(stack.clone());
    int remaining = 0;
    for (ItemStack leftover : leftovers.values()) {
      if (leftover != null) {
        remaining += leftover.getAmount();
      }
    }

    int inserted = Math.max(0, requested - remaining);
    if (remaining > 0) {
      ItemStack restored = stack.clone();
      restored.setAmount(remaining);
      dropRestoredItem(itemLocation, restored);
    }
    if (inserted <= 0) {
      batch.complete(false);
      return;
    }

    if (batch.claimTargetFx() && itemLocation.getWorld() == batch.player.getWorld()) {
      Location eye = batch.player.getEyeLocation();
      fx(itemLocation, FxPriority.TRAIL)
          .line(Particles.ENCHANTMENT_TABLE, eye.getX(), eye.getY(), eye.getZ(), 5)
          .chord(Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.5F, Sound.ENTITY_ARROW_HIT_PLAYER, 0.3F, 1.8F);
    }
    batch.complete(true);
  }

  private void expireTransferOwned(Location itemLocation, ItemStack stack, TransferState transferState) {
    if (transferState.resolve() && itemLocation.getWorld() != null) {
      itemLocation.getWorld().dropItem(itemLocation, stack);
    }
  }

  private int getInventoryCapacity(Player player, ItemStack stack) {
    int requested = stack.getAmount();
    int capacity = 0;
    int maxStackSize = stack.getMaxStackSize();
    for (ItemStack slot : player.getInventory().getStorageContents()) {
      if (slot == null || slot.getType().isAir()) {
        capacity += maxStackSize;
      } else if (slot.isSimilar(stack)) {
        capacity += Math.max(0, maxStackSize - slot.getAmount());
      }
      if (capacity >= requested) {
        return requested;
      }
    }
    return Math.min(requested, capacity);
  }

  private void restoreConsumedItemOwned(Item item, Location location, ItemStack stack) {
    if (item.isValid()) {
      ItemStack current = item.getItemStack();
      if (current.isSimilar(stack) && current.getAmount() + stack.getAmount() <= current.getMaxStackSize()) {
        ItemStack restored = current.clone();
        restored.setAmount(current.getAmount() + stack.getAmount());
        item.setItemStack(restored);
        return;
      }
    }
    dropRestoredItem(location, stack);
  }

  private void dropRestoredItem(Location location, ItemStack stack) {
    if (location.getWorld() != null && !stack.getType().isAir() && stack.getAmount() > 0) {
      J.runAt(location, () -> location.getWorld().dropItem(location, stack));
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
    private final Player player;
    private final UUID playerId;
    private final Location impact;
    private final double radius;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerItemFetched;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger targetFx = new AtomicInteger();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private FetchBatch(Player player, Location impact, double radius, int affectedLimit, int targetFxLimit,
                       double xpPerItemFetched) {
      this.player = player;
      playerId = player.getUniqueId();
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
      J.runEntity(player, this::finish);
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
