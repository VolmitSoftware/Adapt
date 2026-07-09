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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class RiftVoidMagnet extends SimpleAdaptation<RiftVoidMagnet.Config> {
  private final Cooldowns engageThrottle = cooldowns();

  public RiftVoidMagnet() {
    super("rift-void-magnet");
    registerConfiguration(Config.class);
    setIcon(Material.HOPPER_MINECART);
    setInterval(20);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_void_magnet_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_void_magnet_50k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_void_magnet_5k", "rift.void-magnet.items-pulled", 5000, 400);
    registerMilestone("challenge_rift_void_magnet_50k", "rift.void-magnet.items-pulled", 50000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, getMaxItems(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getPulseTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }
    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0 || !engageThrottle.isReady(p.getUniqueId(), 2500L)) {
      return;
    }

    engageThrottle.mark(p.getUniqueId());
    fx(p, FxPriority.TRANSITION)
        .ring(Particles.END_ROD, getRadius(level), 24, 0.1)
        .sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.5f);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }
      int level = getActiveLevel(p, Player::isSneaking);
      if (level <= 0 || p.getTicksLived() % getPulseTicks(level) != 0) {
        continue;
      }

      int moved = collectNearbyItems(p, level);
      if (moved <= 0) {
        continue;
      }

      fx(p, FxPriority.TRAIL)
          .particle(Particle.PORTAL, 8, 0, 1.0, 0, 0.3, 0.05)
          .sound(Sound.BLOCK_ENDER_CHEST_OPEN, 0.45f, Math.min(1.9f, 1.4f + (moved * 0.02f)));
      addStat(p, "rift.void-magnet.items-pulled", moved);
      xp(p, moved * getConfig().xpPerMovedItem, "rift:void-magnet:item-pull");
    }
  }

  private int collectNearbyItems(Player p, int level) {
    int moved = 0;
    int max = getMaxItems(level);
    double r = getRadius(level);
    for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), r, r, r)) {
      if (!(entity instanceof Item item)) {
        continue;
      }

      if (moved >= max || item.isDead() || !item.isValid()) {
        continue;
      }

      if (!canSnatchItem(p, item)) {
        continue;
      }

      ItemStack stack = item.getItemStack();
      if (stack == null || stack.getType().isAir()) {
        continue;
      }

      int requestAmount = Math.min(stack.getAmount(), max - moved);
      if (requestAmount <= 0) {
        continue;
      }

      EntityPickupItemEvent pickupEvent = new EntityPickupItemEvent(p, item, 0);
      Bukkit.getPluginManager().callEvent(pickupEvent);
      if (pickupEvent.isCancelled()) {
        continue;
      }

      ItemStack toChest = stack.clone();
      toChest.setAmount(requestAmount);
      Map<Integer, ItemStack> chestOverflow = p.getEnderChest().addItem(toChest);
      int chestRemaining = sumItemAmounts(chestOverflow);
      int movedAmount = Math.max(0, requestAmount - chestRemaining);

      if (chestRemaining > 0 && getConfig().allowEnderChestOverflow) {
        ItemStack toInventory = stack.clone();
        toInventory.setAmount(chestRemaining);
        Map<Integer, ItemStack> inventoryOverflow = p.getInventory().addItem(toInventory);
        int inventoryRemaining = sumItemAmounts(inventoryOverflow);
        movedAmount += Math.max(0, chestRemaining - inventoryRemaining);
      }

      if (movedAmount <= 0) {
        continue;
      }

      if (movedAmount >= stack.getAmount()) {
        item.remove();
      } else {
        stack.setAmount(stack.getAmount() - movedAmount);
        item.setItemStack(stack);
      }
      moved += movedAmount;
    }

    return moved;
  }

  private int sumItemAmounts(Map<Integer, ItemStack> overflow) {
    int sum = 0;
    for (ItemStack itemStack : overflow.values()) {
      if (itemStack == null || itemStack.getType().isAir()) {
        continue;
      }
      sum += itemStack.getAmount();
    }
    return sum;
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getMaxItems(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxItemsBase + (getLevelPercent(level) * getConfig().maxItemsFactor)));
  }

  private int getPulseTicks(int level) {
    return Math.max(2, (int) Math.round(getConfig().pulseTicksBase - (getLevelPercent(level) * getConfig().pulseTicksFactor)));
  }

  @ConfigDescription("Sneak to periodically pull nearby dropped items into your ender chest first.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Allow Ender Chest Overflow for the Rift Void Magnet adaptation.", impact = "When true, leftovers that do not fit in ender chest can spill into player inventory.")
    boolean allowEnderChestOverflow = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Items Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxItemsBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Items Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxItemsFactor = 22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Ticks Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseTicksBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Ticks Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseTicksFactor = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Moved Item for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMovedItem = 0.7;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
