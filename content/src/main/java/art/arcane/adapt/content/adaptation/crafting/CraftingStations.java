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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;


public class CraftingStations extends SimpleAdaptation<CraftingStations.Config> {
  public static final Id ID = Id.adapt("crafting/stations");
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", Text.key("adapt.crafting.preferences.craftingstations.sneak"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> WORKBENCH = CommonPreferences.toggle("workbench", Text.key("adapt.crafting.preferences.craftingstations.workbench"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> GRINDSTONE = CommonPreferences.toggle("grindstone", Text.key("adapt.crafting.preferences.craftingstations.grindstone"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ANVIL = CommonPreferences.toggle("anvil", Text.key("adapt.crafting.preferences.craftingstations.anvil"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> STONECUTTER = CommonPreferences.toggle("stonecutter", Text.key("adapt.crafting.preferences.craftingstations.stonecutter"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CARTOGRAPHY = CommonPreferences.toggle("cartography", Text.key("adapt.crafting.preferences.craftingstations.cartography"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> LOOM = CommonPreferences.toggle("loom", Text.key("adapt.crafting.preferences.craftingstations.loom"), CommonPreferences.Toggle.ON);

  private final Cooldowns blockedCue = cooldowns();
  private final PortableStations stations = new PortableStations(this);
  private final PortableStations.Feedback feedback = new PortableStations.Feedback() {
    @Override
    public void hungry(Loc handTip) {
      CraftingStations.this.hungry(handTip);
    }

    @Override
    public void opened(Loc handTip) {
      materializeStation(handTip);
    }
  };

  public CraftingStations() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crafting_table"));
    setInterval(9248);
    AdaptAdvancement stations5k = AdaptAdvancement.builder().key("challenge_crafting_stations_5k").icon(Id.minecraft("smithing_table")).title(Text.key("adapt.advancement.challenge_crafting_stations_5k.title")).description(Text.key("adapt.advancement.challenge_crafting_stations_5k.description")).frame(AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.VANILLA).build();
    AdaptAdvancement stations200 = AdaptAdvancement.builder().key("challenge_crafting_stations_200").icon(Id.minecraft("crafting_table")).title(Text.key("adapt.advancement.challenge_crafting_stations_200.title")).description(Text.key("adapt.advancement.challenge_crafting_stations_200.description")).frame(AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.VANILLA).child(stations5k).build();
    registerAdvancement(stations200);
    registerMilestone(stations200.key(), "crafting.stations.portable-opens", 200, 300);
    registerMilestone(stations5k.key(), "crafting.stations.portable-opens", 5000, 1000);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK, WORKBENCH, GRINDSTONE, ANVIL, STONECUTTER, CARTOGRAPHY, LOOM);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.RED + Text.key("adapt.crafting.stations.lore2").resolve());
    v.addLore(TextFormat.GRAY + Text.key("adapt.crafting.stations.lore3").resolve());
    if (getConfig().hungerCost > 0) {
      statLore(v, TextFormat.YELLOW, "* ", getConfig().hungerCost, 4);
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
    CraftingStation station = PortableStations.station(hand.type());

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

    if (stations.open(p, hand.type(), station, getConfig().cooldown, getConfig().hungerCost, feedback)) {
      addStat(p, "crafting.stations.portable-opens", 1);
      Mutations.landed(this, p);
    }
  }

  private void hungry(Loc handTip) {
    fx(handTip, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.15D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
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
            fx.ring(Particles.CRIT, 0.5D, 10, 0.0D);
          }
        })
        .start();
  }


  @ConfigDescription("Use crafting tables, anvils, and other stations in the palm of your hand.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Cooldown for the Crafting Stations adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 125;
    @ConfigDoc(value = "Hunger points consumed each time a portable station is opened.", impact = "Higher values make portable station access cost more food; 0 disables the cost.")
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
