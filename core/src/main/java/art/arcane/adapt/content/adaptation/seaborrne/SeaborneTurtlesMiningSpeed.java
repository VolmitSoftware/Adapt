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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SeaborneTurtlesMiningSpeed extends SimpleAdaptation<SeaborneTurtlesMiningSpeed.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> FLOATING = CommonPreferences.toggle("floating", SeabornMessages.SEABORNETURTLESMININGSPEED_PREFERENCE_FLOATING, CommonPreferences.Toggle.ON);

  public static final List<PlatformNeed> NEEDS = List.of(
      PlatformNeed.attribute(Attributes.SUBMERGED_MINING_SPEED), PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
  private static final String SUBMERGED_ATTRIBUTE_SLOT = "submerged-mining";
  private static final String FLOATING_ATTRIBUTE_SLOT = "floating-mining";
  private static final String FATIGUE_ATTRIBUTE_SLOT = "fatigue";
  private static final double SUBMERGED_MINING_BASE = 0.2D;
  private static final double[] FATIGUE_LEVEL_FACTORS = {0.3D, 0.09D, 0.0027D, 8.1E-4D};
  private static final double FATIGUE_TRIM_HORIZON_TICKS = 20D;
  private static final Id MINING_FATIGUE = Id.minecraft("mining_fatigue");
  private static final int MIN_REFRESH_INTERVAL_MILLIS = 250;
  private static final int MAX_REFRESH_INTERVAL_MILLIS = 10_000;
  private static final long MAX_ATTRIBUTE_DURATION_TICKS = 20L * 60L;
  private static final double MAX_MINING_SPEED_MULTIPLIER = 10D;

  private final Map<UUID, Boolean> submerged = playerState();
  private final Map<UUID, Boolean> floating = playerState();
  private final Map<UUID, Boolean> fatigueCountered = playerState();
  private final Cooldowns breakPulse = cooldowns();

  public SeaborneTurtlesMiningSpeed() {
    super("seaborne-turtles-mining-speed");
    needs(NEEDS.toArray(new PlatformNeed[0]));
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.haste");
    setIcon(Id.minecraft("prismarine_shard"));
    setInterval(refreshIntervalMillis(getConfig().refreshIntervalMillis));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe"))
        .key("challenge_seaborne_mining_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_pickaxe"))
            .key("challenge_seaborne_mining_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_mining_2500", "seaborne.turtles-mining.blocks-underwater", 2500, 300);
    registerMilestone("challenge_seaborne_mining_25k", "seaborne.turtles-mining.blocks-underwater", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FLOATING);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    clearMiningState(player.getPlayer(), player.getPlayer().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(SeabornMessages.HASTE_LORE1));
    statLore(v, Form.pc(Math.min(1D, Math.max(0D, getConfig().fatigueTrimChance)), 0), 2);
  }


  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    Loc at = e.block().center();
    withAdaptedPlayer(p, e, () -> {
      if (!p.isInWater()) {
        return;
      }

      addStat(p, "seaborne.turtles-mining.blocks-underwater", 1);
      if (!breakPulse.isReady(p.uuid(), 150L)) {
        return;
      }
      breakPulse.mark(p.uuid());
      fx(at, FxPriority.AMBIENT)
          .particle(Particles.BUBBLE, 4, 0, 0, 0, 0.05D, 0.02D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.2F, 1.7F);
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> refreshMiningState(p));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    if (submerged.isEmpty()) {
      return;
    }

    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (!submerged.containsKey(id)) {
      return;
    }
    if (getActiveLevel(p) <= 0 || !p.isInWater()) {
      clearMiningState(p, id);
      return;
    }
    if (p.isOnGround()) {
      clearFloatingModifier(p, id);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    submerged.remove(id);
    floating.remove(id);
    fatigueCountered.remove(id);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          return;
        }
        refreshMiningState(player);
      });
    }
  }

  private void refreshMiningState(PlayerRef player) {
    UUID id = player.uuid();
    if (getActiveLevel(player) <= 0 || !player.isInWater()) {
      clearMiningState(player, id);
      return;
    }

    long durationTicks = attributeDurationTicks(getConfig().attributeDurationTicks);
    applySubmerged(this, player, getConfig().underwaterMiningSpeedMultiplier, durationTicks);
    boolean compensateFloating = getConfig().compensateFloatingPenalty && preferenceEnabled(player, FLOATING) && !player.isOnGround();
    if (compensateFloating && applyFloating(this, player, getConfig().floatingMiningSpeedMultiplier, durationTicks)) {
      floating.put(id, true);
    } else {
      clearFloatingModifier(player, id);
    }
    updateFatigueCounter(player, id);

    if (submerged.put(id, true) == null) {
      fx(player.location().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
          .ring(Particles.CRIT, 0.5D, 6, 0.0D)
          .particle(Particles.BUBBLE, 6, 0D, 0.2D, 0D, 0.35D, 0.02D)
          .dustBurst(3, 0.3D, 0.9F)
          .chord(Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.4F, 1.5F, Sounds.BLOCK_CONDUIT_AMBIENT_SHORT, 0.25F, 1.0F);
    }
  }

  private void clearMiningState(PlayerRef player, UUID id) {
    if (submerged.remove(id) != null) {
      removeSubmerged(this, player);
    }
    clearFloatingModifier(player, id);
    clearFatigueCounter(player, id);
  }

  private void updateFatigueCounter(PlayerRef player, UUID id) {
    EffectInstance fatigue = player.effect(MINING_FATIGUE);
    long durationTicks = fatigue == null ? 0L : counterDurationTicks(fatigue.durationTicks(), getConfig().fatigueCounterDurationTicks);
    double bonus = fatigue == null ? 0D : fatigueCounterBonus(getConfig().fatigueTrimChance, getConfig().fatigueTrimAmount,
        fatigue.amplifier());
    if (durationTicks <= 0L || bonus <= 0D) {
      clearFatigueCounter(player, id);
      return;
    }
    AdaptAttributeService.get().applyTimed(player, getName(), FATIGUE_ATTRIBUTE_SLOT, Attributes.SUBMERGED_MINING_SPEED,
        bonus, AttributeOperation.ADD_VALUE, durationTicks);
    if (fatigueCountered.put(id, true) == null) {
      fx(player.location().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
          .particle(Particles.CRIT, 4, 0D, 0.3D, 0D, 0.3D, 0.05D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.6F);
    }
  }

  private void clearFatigueCounter(PlayerRef player, UUID id) {
    if (fatigueCountered.remove(id) != null) {
      AdaptAttributeService.get().remove(player, getName(), FATIGUE_ATTRIBUTE_SLOT, Attributes.SUBMERGED_MINING_SPEED);
    }
  }

  static long counterDurationTicks(int fatigueDurationTicks, int configuredCounterTicks) {
    if (configuredCounterTicks <= 0) {
      return 0L;
    }
    long bounded = Math.min(MAX_ATTRIBUTE_DURATION_TICKS, configuredCounterTicks);
    if (fatigueDurationTicks < 0) {
      return Math.max(20L, bounded);
    }
    return Math.max(20L, Math.min(fatigueDurationTicks, bounded));
  }

  /** Underwater mining speed that offsets {@code trimChance} of the slowdown each tick, as a vanilla attribute bonus. */
  static double fatigueCounterBonus(double trimChance, int trimAmount, int fatigueAmplifier) {
    double chance = Math.max(0D, Math.min(1D, trimChance));
    double trimPerSecond = chance * FATIGUE_TRIM_HORIZON_TICKS * Math.max(0, trimAmount);
    int amplifier = Math.max(0, fatigueAmplifier);
    double reducedAmplifier = Math.max(0D, amplifier - trimPerSecond);
    double current = fatigueFactor(amplifier);
    double target = fatigueFactor(reducedAmplifier);
    if (target <= current) {
      return 0D;
    }
    return SUBMERGED_MINING_BASE * (target / current - 1D);
  }

  static double fatigueFactor(double amplifier) {
    double clamped = Math.max(0D, Math.min(3D, amplifier));
    int lower = (int) Math.floor(clamped);
    int upper = (int) Math.ceil(clamped);
    double lowerLog = Math.log(FATIGUE_LEVEL_FACTORS[lower]);
    double upperLog = Math.log(FATIGUE_LEVEL_FACTORS[upper]);
    return Math.exp(lowerLog + (upperLog - lowerLog) * (clamped - lower));
  }

  private void clearFloatingModifier(PlayerRef player, UUID id) {
    if (floating.remove(id) != null) {
      removeFloating(this, player);
    }
  }

  /** Multiplies {@code player}'s underwater mining speed by {@code multiplier}, owned by {@code owner}; 1 or less removes it. */
  public static void applySubmerged(Adaptation<?> owner, PlayerRef player, double multiplier, long durationTicks) {
    double scalar = multiplierScalar(multiplier);
    if (scalar > 0D) {
      AdaptAttributeService.get().applyTimed(player, owner.getName(), SUBMERGED_ATTRIBUTE_SLOT,
          Attributes.SUBMERGED_MINING_SPEED, scalar, AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
    } else {
      removeSubmerged(owner, player);
    }
  }

  public static void removeSubmerged(Adaptation<?> owner, PlayerRef player) {
    AdaptAttributeService.get().remove(player, owner.getName(), SUBMERGED_ATTRIBUTE_SLOT, Attributes.SUBMERGED_MINING_SPEED);
  }

  /** Multiplies {@code player}'s block break speed by {@code multiplier} to cancel the floating penalty; false when nothing was applied. */
  public static boolean applyFloating(Adaptation<?> owner, PlayerRef player, double multiplier, long durationTicks) {
    double scalar = multiplierScalar(multiplier);
    if (scalar <= 0D) {
      return false;
    }
    AdaptAttributeService.get().applyTimed(player, owner.getName(), FLOATING_ATTRIBUTE_SLOT, Attributes.BLOCK_BREAK_SPEED,
        scalar, AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
    return true;
  }

  public static void removeFloating(Adaptation<?> owner, PlayerRef player) {
    AdaptAttributeService.get().remove(player, owner.getName(), FLOATING_ATTRIBUTE_SLOT, Attributes.BLOCK_BREAK_SPEED);
  }

  static double multiplierScalar(double multiplier) {
    if (!Double.isFinite(multiplier)) {
      return 0D;
    }
    return Math.max(0D, Math.min(MAX_MINING_SPEED_MULTIPLIER, multiplier) - 1D);
  }

  static long attributeDurationTicks(long configuredTicks) {
    return Math.max(20L, Math.min(MAX_ATTRIBUTE_DURATION_TICKS, configuredTicks));
  }

  static int refreshIntervalMillis(int configuredMillis) {
    return Math.max(MIN_REFRESH_INTERVAL_MILLIS, Math.min(MAX_REFRESH_INTERVAL_MILLIS, configuredMillis));
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    setInterval(refreshIntervalMillis(newConfig.refreshIntervalMillis));
  }

  @ConfigDescription("Mine faster while underwater, with compensation for the vanilla floating penalty, and shrug off most of a guardian's Mining Fatigue.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Mining speed multiplier applied specifically while submerged.", impact = "1.4 matches the previous 40% underwater boost; 1 disables this part of the bonus.")
    double underwaterMiningSpeedMultiplier = 1.4D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cancels the separate vanilla airborne mining slowdown while the player floats underwater.", impact = "True makes the adaptation equally effective when swimming or standing on the seabed.")
    boolean compensateFloatingPenalty = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Block break speed multiplier used while floating underwater when compensation is enabled.", impact = "5 cancels vanilla's one-fifth airborne mining speed; 1 disables the compensation boost.")
    double floatingMiningSpeedMultiplier = 5D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshed mining attribute modifier.", impact = "Keep this above the refresh interval converted to ticks to avoid gaps between refreshes.")
    long attributeDurationTicks = 160L;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between passive underwater-state refreshes.", impact = "Lower values react faster but scan learned players more frequently; block damage still activates the boost immediately.")
    int refreshIntervalMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Share of Mining Fatigue's slowdown cancelled each tick while you mine underwater.", impact = "Higher values offset more of Mining Fatigue II and above; Mining Fatigue I remains the floor.")
    double fatigueTrimChance = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Mining Fatigue amplifier steps each cancelled tick removes.", impact = "Higher values counter stronger Mining Fatigue more aggressively.")
    int fatigueTrimAmount = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshed counter while Mining Fatigue is present.", impact = "Higher values tolerate refresh delays; zero disables fatigue countering.")
    int fatigueCounterDurationTicks = 80;

    public Config() {
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 4;
    }
  }
}
