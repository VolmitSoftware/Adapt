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

package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.world.BlockOwners;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.UUID;

public class BrewingLingering extends SimpleAdaptation<BrewingLingering.Config> {
  public static final Id ID = Id.adapt("brewing/lingering");
  public static final PlayerPreference<CommonPreferences.Toggle> LORE = CommonPreferences.toggle("extended-lore", Text.key("adapt.brewing.preferences.brewinglingering.extended-lore"), CommonPreferences.Toggle.ON);

  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");

  public BrewingLingering() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("dragon_breath"));
    setInterval(4788);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lingering_potion"))
        .key("challenge_brewing_lingering_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dragon_breath"))
            .key("challenge_brewing_lingering_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brewing_lingering_200", "brewing.lingering.potions-extended", 200, 300);
    registerMilestone("challenge_brewing_lingering_5k", "brewing.lingering.potions-extended", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, LORE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.duration((long) getDurationBoost(getLevelPercent(level)), 0), 1);
    statLore(v, Format.pc(getPercentBoost(getLevelPercent(level)), 0), 2);
  }

  public double getDurationBoost(double factor) {
    return (getConfig().durationBoostFactorTicks * factor) + getConfig().baseDurationBoostTicks;
  }

  public double getPercentBoost(double factor) {
    return 1 + ((factor * factor * getConfig().durationMultiplierFactor) + getConfig().baseDurationMultiplier);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BrewEvent e) {
    if (!e.block().type().equals(BREWING_STAND)) {
      return;
    }
    UUID owner = BlockOwners.owner(e.block()).orElse(null);

    if (owner == null) {
      AdaptApi.log().verbose("No Owner");
      return;
    }

    int level = ownerLevel(owner);
    if (level <= 0) {
      return;
    }

    int enhancedPotions = BrewingDuration.extendAll(e.results(), getDurationBoost(getLevelPercent(level)),
        getPercentBoost(getLevelPercent(level)),
        getConfig().useCustomLore && preferenceEnabled(AdaptApi.platform().host().player(owner), LORE));
    if (enhancedPotions > 0) {
      AdaptApi.players().find(owner).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.lingering.potions-extended", enhancedPotions));
      Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
      ParticleData.DustTransition transition = new ParticleData.DustTransition(new Rgb(0x8A, 0x2B, 0xE2), new Rgb(0xFF, 0x77, 0xFF), 1.2F);
      timeline(loc)
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .cullRadius(24.0D)
          .frame((f, tick, progress) -> {
            f.ring(Particles.DUST_COLOR_TRANSITION, 0.4D + (0.6D * progress), 10, 0.3D, transition);
            if (tick == 0) {
              f.particle(Particles.DRAGON_BREATH, 16, 0, 0.2D, 0, 0.4D, 0.01D, new ParticleData.Power(1.0F))
                  .chord(Sounds.BLOCK_BREWING_STAND_BREW, 1.0F, 0.75F, Sounds.BLOCK_BREWING_STAND_BREW, 1.0F, 1.75F, Sounds.ENTITY_ENDER_DRAGON_FLAP, 0.3F, 1.2F)
                  .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.2F);
            }
            if (tick == 3) {
              f.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
            }
          })
          .start();
    }
  }

  @ConfigDescription("Brewed potions last longer.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Base Duration Boost Ticks for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDurationBoostTicks = 100;
    @ConfigDoc(value = "Controls Duration Boost Factor Ticks for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationBoostFactorTicks = 500;
    @ConfigDoc(value = "Controls Duration Multiplier Factor for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationMultiplierFactor = 0.45;
    @ConfigDoc(value = "Controls Base Duration Multiplier for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDurationMultiplier = 0.05;
    @ConfigDoc(value = "Controls Use Custom Lore for the Brewing Lingering adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useCustomLore = true;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
