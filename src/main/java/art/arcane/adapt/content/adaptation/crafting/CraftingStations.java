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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


public class CraftingStations extends SimpleAdaptation<CraftingStations.Config> {
  private final Cooldowns blockedCue = cooldowns();

  public CraftingStations() {
    super("crafting-stations");
    registerConfiguration(Config.class);
    setIcon(Material.CRAFTING_TABLE);
    setInterval(9248);
    AdvancementSpec stations5k = AdvancementSpec.challenge(
        "challenge_crafting_stations_5k",
        Material.SMITHING_TABLE,
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_5K_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_5K_DESCRIPTION)
    );
    AdvancementSpec stations200 = AdvancementSpec.challenge(
        "challenge_crafting_stations_200",
        Material.CRAFTING_TABLE,
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_200_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_200_DESCRIPTION)
    ).withChild(stations5k);
    registerAdvancementSpec(stations200);
    registerStatTracker(stations200.statTracker("crafting.stations.portable-opens", 200, 300));
    registerStatTracker(stations5k.statTracker("crafting.stations.portable-opens", 5000, 1000));
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.RED + AdaptLanguage.text(CraftingMessages.STATIONS_LORE2));
    v.addLore(C.GRAY + AdaptLanguage.text(CraftingMessages.STATIONS_LORE3));
    if (getConfig().hungerCost > 0) {
      statLore(v, C.YELLOW, "* ", getConfig().hungerCost, 4);
    }
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    InventoryType station = switch (hand.getType()) {
      case CRAFTING_TABLE -> InventoryType.WORKBENCH;
      case GRINDSTONE -> InventoryType.GRINDSTONE;
      case ANVIL -> InventoryType.ANVIL;
      case STONECUTTER -> InventoryType.STONECUTTER;
      case CARTOGRAPHY_TABLE -> InventoryType.CARTOGRAPHY;
      case LOOM -> InventoryType.LOOM;
      default -> null;
    };

    if (station == null) {
      return;
    }

    if (p.hasCooldown(hand.getType())) {
      e.setCancelled(true);
      if (blockedCue.isReady(p.getUniqueId(), 700)) {
        blockedCue.mark(p.getUniqueId());
        fx(p.getLocation(), FxPriority.TRANSITION).sound(Sound.BLOCK_DISPENSER_FAIL, 0.3F, 1.0F);
      }
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    Location handTip = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8D));
    if (hungerCost > 0 && p.getFoodLevel() < hungerCost) {
      fx(handTip, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.15D)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    }

    p.setCooldown(hand.getType(), getConfig().cooldown);
    materializeStation(handTip);
    switch (station) {
      case WORKBENCH -> p.openWorkbench(null, true);
      case GRINDSTONE -> p.openGrindstone(null, true);
      case ANVIL -> p.openAnvil(null, true);
      case STONECUTTER -> p.openStonecutter(null, true);
      case CARTOGRAPHY -> p.openCartographyTable(null, true);
      case LOOM -> p.openLoom(null, true);
      default -> {
      }
    }
    addStat(p, "crafting.stations.portable-opens", 1);
  }

  private void materializeStation(Location handTip) {
    timeline(handTip)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16)
        .frame((fx, tick, progress) -> {
          fx.particle(Particle.REVERSE_PORTAL, 3, 0, 0, 0, 0.25D * (1.0D - progress), 0.02D);
          if (tick == 0) {
            fx.particle(Particle.ENCHANT, 8, 0, 0, 0, 0.3D, 0.05D);
            fx.chord(Sound.PARTICLE_SOUL_ESCAPE, 1F, 0.10F, Sound.BLOCK_ENDER_CHEST_OPEN, 1F, 0.10F);
            fx.chord(Sound.BLOCK_BEACON_ACTIVATE, 0.3F, 1.8F, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4F, 1.0F);
          }
          if (tick >= 4) {
            fx.ring(Particles.CRIT_MAGIC, 0.5D, 10, 0.0D);
          }
        })
        .start();
  }


  @ConfigDescription("Use crafting tables, anvils, and other stations in the palm of your hand.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Crafting Stations adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 125;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed each time a portable station is opened.", impact = "Higher values make portable station access cost more food; 0 disables the cost.")
    int hungerCost = 2;

    public Config() {
      permanent = true;
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
