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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Set;

public class ChronosAccelerate extends SimpleAdaptation<ChronosAccelerate.Config> {
  public static final Set<Id> FURNACE_TYPES = Set.of(Id.minecraft("furnace"), Id.minecraft("blast_furnace"), Id.minecraft("smoker"));
  public static final Id BREWING_STAND = Id.minecraft("brewing_stand");
  public static final PlayerPreference<CommonPreferences.Toggle> CROPS = CommonPreferences.toggle("crops", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_CROPS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FURNACES = CommonPreferences.toggle("furnaces", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_FURNACES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BREWING = CommonPreferences.toggle("brewing", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_BREWING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> RADIUS = CommonPreferences.scale("radius", ChronosMessages.PREFERENCE_CHRONOSACCELERATE_RADIUS);

  private final ChronosAura aura = new ChronosAura(this, new ChronosAura.Tuning() {
    @Override
    public long pulseIntervalMillis() {
      return getConfig().pulseIntervalMillis;
    }

    @Override
    public int maxPlayersPerPass() {
      return getConfig().maxPlayersPerPass;
    }

    @Override
    public int maxSamplesPerPass() {
      return getConfig().maxSamplesPerPass;
    }

    @Override
    public int samples(int level) {
      return getSamples(level);
    }

    @Override
    public double radius(int level) {
      return getRadius(level);
    }

    @Override
    public double radiusScale(PlayerRef player) {
      return preference(player, RADIUS).multiplier();
    }

    @Override
    public double growChance(int level) {
      return getGrowChance(level);
    }

    @Override
    public double cookBoost(int level) {
      return getCookBoostFraction(level);
    }

    @Override
    public boolean crops(PlayerRef player) {
      return preferenceEnabled(player, CROPS);
    }

    @Override
    public boolean furnaces(PlayerRef player) {
      return preferenceEnabled(player, FURNACES);
    }

    @Override
    public boolean brewing(PlayerRef player) {
      return preferenceEnabled(player, BREWING);
    }

    @Override
    public void accelerated(PlayerRef player, int count) {
      addStat(player, "chronos.accelerate.blocks-accelerated", count);
      xpSilent(player, count * getConfig().xpPerAcceleratedBlock, "chronos:accelerate");
    }
  }, playerState());

  public ChronosAccelerate() {
    super("chronos-accelerate");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sugar"));
    setInterval(getConfig().pulseIntervalMillis);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("sugar"))
        .key("challenge_chronos_accelerate_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_accelerate_1k", "chronos.accelerate.blocks-accelerated", 1000, 600);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CROPS, FURNACES, BREWING, RADIUS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, C.YELLOW, "+ ", Math.round(getGrowChance(level) * 100D) + "%", 2);
    statLore(v, C.GRAY, "* ", Math.round(getCookBoostFraction(level) * 100D) + "%", 3);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    aura.forget(e.player().uuid());
  }

  private double getRadius(int level) {
    return getConfig().baseRadius + (Math.max(1, level) * getConfig().radiusPerLevel);
  }

  private double getGrowChance(int level) {
    return Math.max(0D, Math.min(1D, getConfig().baseGrowChance + (Math.max(1, level) * getConfig().growChancePerLevel)));
  }

  private int getSamples(int level) {
    return Math.min(getConfig().maxSamplesPerPulse, getConfig().baseSamplesPerPulse + level);
  }

  private double getCookBoostFraction(int level) {
    return Math.min(getConfig().maxCookBoostFraction,
        getConfig().baseCookBoostFraction + (Math.max(1, level) * getConfig().cookBoostFractionPerLevel));
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    aura.tick();
  }

  public static void cropFx(Adaptation<?> owner, WorldRef world, int x, int y, int z) {
    Fx.now(owner, new Loc(world, x + 0.5, y + 0.9, z + 0.5), FxPriority.AMBIENT)
        .particle(Particles.VILLAGER_HAPPY, 3, 0, 0.1D, 0, 0.2D, 0.01D)
        .particle(Particles.COMPOSTER, 1, 0, 0.2D, 0, 0.15D, 0);
  }

  public static void stationFx(Adaptation<?> owner, WorldRef world, int x, int y, int z, boolean furnace) {
    Fx.now(owner, new Loc(world, x + 0.5, y + 0.6, z + 0.5), FxPriority.AMBIENT)
        .particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0.3D, 0, 0.25D, 0.02D)
        .particle(furnace ? Particles.SMOKE : Particles.SPLASH, 1, 0, 0.3D, 0, 0.1D, 0.01D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.6F);
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    setInterval(Math.max(1L, newConfig.pulseIntervalMillis));
  }

  @ConfigDescription("Passively accelerate time around you, occasionally growing nearby crops and speeding furnaces and brewing stands.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between acceleration pulses.", impact = "Lower values pulse more often at more server cost.")
    long pulseIntervalMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base aura radius in blocks.", impact = "Higher values sample blocks from a wider area.")
    double baseRadius = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra aura radius per adaptation level.", impact = "Higher values make leveling widen the aura faster.")
    double radiusPerLevel = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of random blocks sampled per pulse.", impact = "Higher values accelerate more blocks at more server cost.")
    int baseSamplesPerPulse = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on blocks sampled per pulse.", impact = "Higher values raise the per pulse work ceiling.")
    int maxSamplesPerPulse = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum learned players prepared in one acceleration pass.", impact = "Higher values refresh more players immediately; lower values spread owner-thread scheduling across passes.")
    int maxPlayersPerPass = 512;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum block-region jobs admitted by one acceleration pass.", impact = "Higher values preserve more simultaneous aura pulses; lower values shed work sooner under extreme populations.")
    int maxSamplesPerPass = 8192;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance for a sampled crop to advance one growth stage.", impact = "Higher values grow crops faster.")
    double baseGrowChance = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra crop growth chance per adaptation level.", impact = "Higher values make leveling grow crops faster.")
    double growChancePerLevel = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of total cook or brew time fast-forwarded per pulse hit.", impact = "Higher values complete cooks and brews in fewer pulses.")
    double baseCookBoostFraction = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra cook fast-forward fraction per adaptation level.", impact = "Higher values make leveling speed cooking faster.")
    double cookBoostFractionPerLevel = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the cook fast-forward fraction per pulse hit.", impact = "Higher values allow nearly instant cooks at high levels.")
    double maxCookBoostFraction = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per accelerated block.", impact = "Higher values grant more skill XP from the aura.")
    double xpPerAcceleratedBlock = 1.2;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
