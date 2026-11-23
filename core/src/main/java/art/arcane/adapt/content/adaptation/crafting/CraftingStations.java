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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;


public class CraftingStations extends SimpleAdaptation<CraftingStations.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", CraftingMessages.PREFERENCE_CRAFTINGSTATIONS_SNEAK, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> WORKBENCH = CommonPreferences.toggle("workbench", CraftingMessages.PREFERENCE_CRAFTINGSTATIONS_WORKBENCH, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> GRINDSTONE = CommonPreferences.toggle("grindstone", CraftingMessages.PREFERENCE_CRAFTINGSTATIONS_GRINDSTONE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ANVIL = CommonPreferences.toggle("anvil", CraftingMessages.PREFERENCE_CRAFTINGSTATIONS_ANVIL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> STONECUTTER = CommonPreferences.toggle("stonecutter", CraftingMessages.PREFERENCE_CRAFTINGSTATIONS_STONECUTTER, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CARTOGRAPHY = CommonPreferences.toggle("cartography", CraftingMessages.PREFERENCE_CRAFTINGSTATIONS_CARTOGRAPHY, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> LOOM = CommonPreferences.toggle("loom", CraftingMessages.PREFERENCE_CRAFTINGSTATIONS_LOOM, CommonPreferences.Toggle.ON);

  private final Cooldowns blockedCue = cooldowns();

  public CraftingStations() {
    super("crafting-stations");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crafting_table"));
    setInterval(9248);
    AdvancementSpec stations5k = AdvancementSpec.challenge(
        "challenge_crafting_stations_5k",
        Id.minecraft("smithing_table"),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_5K_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_5K_DESCRIPTION)
    );
    AdvancementSpec stations200 = AdvancementSpec.challenge(
        "challenge_crafting_stations_200",
        Id.minecraft("crafting_table"),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_200_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_STATIONS_200_DESCRIPTION)
    ).withChild(stations5k);
    registerAdvancementSpec(stations200);
    registerStatTracker(stations200.statTracker("crafting.stations.portable-opens", 200, 300));
    registerStatTracker(stations5k.statTracker("crafting.stations.portable-opens", 5000, 1000));
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK, WORKBENCH, GRINDSTONE, ANVIL, STONECUTTER, CARTOGRAPHY, LOOM);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.RED + AdaptLanguage.text(CraftingMessages.STATIONS_LORE2));
    v.addLore(C.GRAY + AdaptLanguage.text(CraftingMessages.STATIONS_LORE3));
    if (getConfig().hungerCost > 0) {
      statLore(v, C.YELLOW, "* ", getConfig().hungerCost, 4);
    }
  }

  @Handle
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    CraftingStation station = station(hand.type());

    if (station == null || preferenceEnabled(p, SNEAK) && !p.isSneaking()
        || station != null && !preferenceEnabled(p, switch (station) {
          case WORKBENCH -> WORKBENCH;
          case GRINDSTONE -> GRINDSTONE;
          case ANVIL -> ANVIL;
          case STONECUTTER -> STONECUTTER;
          case CARTOGRAPHY -> CARTOGRAPHY;
          case LOOM -> LOOM;
        })) {
      return;
    }

    if (p.hasCooldown(hand.type())) {
      e.setCancelled(true);
      if (blockedCue.isReady(p.uuid(), 700)) {
        blockedCue.mark(p.uuid());
        fx(p.location(), FxPriority.TRANSITION).sound(Sounds.BLOCK_DISPENSER_FAIL, 0.3F, 1.0F);
      }
      return;
    }

    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    Loc handTip = p.eyeLocation().add(p.location().direction().multiply(0.8D));
    if (hungerCost > 0 && p.foodLevel() < hungerCost) {
      fx(handTip, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.15D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }

    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    }

    p.setCooldown(hand.type(), getConfig().cooldown);
    AbilityGlyphs.cooldown(p, this, getConfig().cooldown * 50L);
    materializeStation(handTip);
    p.openStation(station);
    addStat(p, "crafting.stations.portable-opens", 1);
  }

  private static CraftingStation station(Id type) {
    if (!type.isMinecraft()) {
      return null;
    }
    return switch (type.path()) {
      case "crafting_table" -> CraftingStation.WORKBENCH;
      case "grindstone" -> CraftingStation.GRINDSTONE;
      case "anvil" -> CraftingStation.ANVIL;
      case "stonecutter" -> CraftingStation.STONECUTTER;
      case "cartography_table" -> CraftingStation.CARTOGRAPHY;
      case "loom" -> CraftingStation.LOOM;
      default -> null;
    };
  }

  private void materializeStation(Loc handTip) {
    timeline(handTip)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.REVERSE_PORTAL, 3, 0, 0, 0, 0.25D * (1.0D - progress), 0.02D);
          if (tick == 0) {
            fx.particle(Particles.ENCHANT, 8, 0, 0, 0, 0.3D, 0.05D);
            fx.chord(Sounds.PARTICLE_SOUL_ESCAPE, 1F, 0.10F, Sounds.BLOCK_ENDER_CHEST_OPEN, 1F, 0.10F);
            fx.chord(Sounds.BLOCK_BEACON_ACTIVATE, 0.3F, 1.8F, Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.4F, 1.0F);
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
      initialCost = 4;
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
