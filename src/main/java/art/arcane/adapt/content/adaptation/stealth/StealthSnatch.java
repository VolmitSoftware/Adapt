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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.inventorygui.Inventories;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class StealthSnatch extends SimpleAdaptation<StealthSnatch.Config> {
  private final Set<Integer> holds;

  public StealthSnatch() {
    super("stealth-snatch");
    registerConfiguration(Config.class);
    setIcon(Material.CHEST_MINECART);
    setInterval(getConfig().snatchRate);
    holds = ConcurrentHashMap.newKeySet();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_stealth_snatch_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.HOPPER)
            .key("challenge_stealth_snatch_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_snatch_2500", "stealth.snatch.items-snatched", 2500, 400);
    registerMilestone("challenge_stealth_snatch_25k", "stealth.snatch.items-snatched", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(getLevelPercent(level)), 1), 1);
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (e.isSneaking()) {
      double range = getRange(getLevelPercent(p));
      timeline(p).duration(6).priority(FxPriority.TRANSITION).cullRadius(range + 8)
          .frame((fx, tick, progress) -> {
            fx.ring(Particle.ENCHANT, 0.3D + ((range - 0.3D) * progress), 10, 0.1D);
            if (tick == 0) {
              fx.sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4F, 1.5F);
            }
          }).start();
      snatch(p);
    }
  }

  private void snatch(Player player) {
    double factor = getLevelPercent(player);

    if (factor == 0) {
      return;
    }

    if (!canAccessChest(player, player.getLocation())) {
      return;
    }

    double range = getRange(factor);
    HashSet<Item> items = new HashSet<>();
    for (Entity droppedItemEntity : player.getWorld().getNearbyEntities(player.getLocation(), range, range / 1.5, range)) {
      if (droppedItemEntity instanceof Item droppedItem && canSnatchItem(player, droppedItem)) {
        items.add(droppedItem);
      }
    }

    int fxBudget = 3;
    int snatched = 0;
    for (Item droppedItemEntity : items) {
      if (holds.contains(droppedItemEntity.getEntityId())) {
        continue;
      }

      double dist = droppedItemEntity.getLocation().distanceSquared(player.getLocation());
      if (dist >= range * range) {
        continue;
      }

      ItemStack is = droppedItemEntity.getItemStack().clone();
      if (!Inventories.hasSpace(player.getInventory(), is)) {
        continue;
      }

      holds.add(droppedItemEntity.getEntityId());
      int id = droppedItemEntity.getEntityId();
      Location itemLoc = fxBudget > 0 ? droppedItemEntity.getLocation() : null;
      if (safeGiveItem(player, droppedItemEntity, is)) {
        snatched++;
        if (itemLoc != null && fxBudget-- > 0) {
          Location target = player.getLocation().add(0, 1.0D, 0);
          float pitch = (float) (1.0D + (ThreadLocalRandom.current().nextDouble() / 3D));
          float pickupPitch = (float) (1.4D + (ThreadLocalRandom.current().nextDouble() * 0.4D));
          fx(itemLoc, FxPriority.TRAIL)
              .line(Particle.ENCHANT, target.getX(), target.getY(), target.getZ(), 4)
              .chord(Sound.BLOCK_LAVA_POP, 0.6F, pitch, Sound.ENTITY_ITEM_PICKUP, 0.5F, pickupPitch);
        }
        addStat(player, "stealth.snatch.items-snatched", 1);
      }
      J.runEntity(player, () -> holds.remove(Integer.valueOf(id)), 1);
    }

    if (snatched > 3) {
      fx(player.getLocation().add(0, 1.0D, 0), FxPriority.TRAIL).burst(Particle.ENCHANT, 5, 0.3D);
    }
  }

  private double getRange(double factor) {
    return (factor * getConfig().radiusFactor) + 1;
  }

  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player i = adaptPlayer.getPlayer();
      if (i.isSneaking()) {
        J.runEntity(i, () -> snatch(i));
      }
    }
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    setInterval(newConfig.snatchRate);
  }

  @ConfigDescription("Snatch dropped items instantly while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Snatch Rate for the Stealth Snatch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int snatchRate = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Stealth Snatch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 5.55;

    public Config() {
      costFactor = 0.125;
      maxLevel = 3;
      initialCost = 12;
    }
  }
}
