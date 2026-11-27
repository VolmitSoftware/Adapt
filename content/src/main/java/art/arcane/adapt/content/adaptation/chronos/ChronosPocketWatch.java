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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChronosPocketWatch extends SimpleAdaptation<ChronosPocketWatch.Config> {
  public static final Id ID = Id.adapt("chronos/pocket_watch");
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", Text.key("adapt.chronos.preferences.chronospocketwatch.require-sneak"), CommonPreferences.Toggle.ON);

  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");
  private static final Id CLOCK = Id.minecraft("clock");
  private static final double MAX_DESCENT_PER_TICK = 0.49D;
  private static final long PULSE_MILLIS = 250L;
  private static final int HARD_MAX_PLAYERS_PER_PASS = 512;
  private static final int MAX_CATCH_UP_PULSES = 4;

  private final Map<UUID, Long> airBudgetMillis = playerState();
  private final Map<UUID, Long> nextPulseAt = playerState();
  private final Map<UUID, FallEffect> fallingEffects = playerState();
  private final Map<UUID, Boolean> applyingEffects = playerState();
  private int playerCursor;

  public ChronosPocketWatch() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("feather"));
    setInterval(PULSE_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("feather"))
        .key("challenge_chronos_pocket_watch_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_pocket_watch_500", "chronos.pocket-watch.slow-fall-seconds", 500, 650);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, (getBudgetMillis(level) / 1000D) + "s", 1);
    v.addLore(TextFormat.YELLOW + "* " + Text.key("adapt.chronos.pocket_watch.lore2").resolve());
    v.addLore(TextFormat.GRAY + "* " + Text.key("adapt.chronos.pocket_watch.lore3").resolve());
  }

  private long getBudgetMillis(int level) {
    return (long) ((getConfig().baseBudgetSeconds + (Math.max(1, level) * getConfig().budgetSecondsPerLevel)) * 1000D);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    clearFallEffect(e.player());
    UUID playerId = e.player().uuid();
    airBudgetMillis.remove(playerId);
    nextPulseAt.remove(playerId);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    clearFallEffect(player.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPotionEffectEvent event) {
    if (event.entity() instanceof PlayerRef player
        && SLOW_FALLING.equals(event.modifiedType())
        && !applyingEffects.containsKey(player.uuid())) {
      fallingEffects.remove(player.uuid());
    }
  }

  private void applyFallEffect(PlayerRef player) {
    UUID playerId = player.uuid();
    int now = player.ticksLived();
    FallEffect owned = fallingEffects.get(playerId);
    EffectInstance previous = owned == null ? player.effect(SLOW_FALLING) : owned.previous();
    int previousStartedAt = owned == null ? now : owned.previousStartedAt();
    applyingEffects.put(playerId, true);
    try {
      if (player.addEffect(EffectInstance.of(SLOW_FALLING, getConfig().pulseDurationTicks, 0).withAmbient(true).withParticles(false).withIcon(false))) {
        fallingEffects.put(playerId, new FallEffect(previous, previousStartedAt));
        Vec3 velocity = player.velocity();
        if (velocity.y() < -MAX_DESCENT_PER_TICK) {
          player.setVelocity(velocity.withY(-MAX_DESCENT_PER_TICK));
        }
      }
    } finally {
      applyingEffects.remove(playerId);
    }
  }

  private void clearFallEffect(PlayerRef player) {
    if (player == null) {
      return;
    }
    UUID playerId = player.uuid();
    FallEffect owned = fallingEffects.remove(playerId);
    if (owned == null) {
      return;
    }
    applyingEffects.put(playerId, true);
    try {
      player.removeEffect(SLOW_FALLING);
      EffectInstance previous = owned.previous();
      if (previous == null) {
        return;
      }
      int remaining = previous.isInfinite() ? EffectInstance.INFINITE
          : previous.durationTicks() - Math.max(0, player.ticksLived() - owned.previousStartedAt());
      if (previous.isInfinite() || remaining > 0) {
        player.addEffect(EffectInstance.of(previous.type(), remaining, previous.amplifier()).withAmbient(previous.ambient()).withParticles(previous.particles()).withIcon(previous.icon()));
      }
    } finally {
      applyingEffects.remove(playerId);
    }
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    List<PlayerProfile> candidates = learnedCandidates(now);
    ChronosWorkBudget.Batch batch = ChronosWorkBudget.batch(
        candidates.size(), playerCursor, getConfig().maxPlayersPerPass, HARD_MAX_PLAYERS_PER_PASS);
    for (int i = 0; i < batch.count(); i++) {
      PlayerProfile adaptPlayer = candidates.get((batch.start() + i) % candidates.size());
      PlayerRef player = adaptPlayer.player();
      if (player != null) {
        AdaptApi.scheduler().runEntity(player, () -> pulsePlayer(player, now));
      }
    }
    playerCursor = batch.nextCursor();
  }

  private void pulsePlayer(PlayerRef player, long now) {
    if (!player.isOnline() || player.isDead()) {
      return;
    }

    UUID playerId = player.uuid();
    if (player.isOnGround()) {
      if (airBudgetMillis.remove(playerId) != null) {
        HudStatus.clear(playerId, this, HudGlyph.DURATION);
      }
      nextPulseAt.remove(playerId);
      return;
    }

    int level = getActiveLevel(player, candidate -> !preferenceEnabled(candidate, SNEAK) || candidate.isSneaking());
    if (level <= 0
        || player.isFlying()
        || player.isGliding()
        || player.vehicle() != null
        || player.isSwimming()
        || (!airBudgetMillis.containsKey(playerId) && player.fallDistance() < getConfig().minFallDistance)
        || (getConfig().requireClock && !player.inventory().contains(CLOCK, 1))) {
      if (nextPulseAt.remove(playerId) != null) {
        HudStatus.clear(playerId, this, HudGlyph.DURATION);
      }
      return;
    }

    Long previousPulseAt = nextPulseAt.get(playerId);
    ChronosWorkBudget.Pulse pulse = ChronosWorkBudget.pulse(
        now, previousPulseAt, PULSE_MILLIS, MAX_CATCH_UP_PULSES);
    nextPulseAt.put(playerId, pulse.nextAt());
    if (pulse.count() <= 0) {
      return;
    }

    long maximum = getBudgetMillis(level);
    boolean firstPulse = !airBudgetMillis.containsKey(playerId);
    long budget = airBudgetMillis.computeIfAbsent(playerId, key -> maximum);
    int appliedPulses = Math.min(pulse.count(), (int) (budget / PULSE_MILLIS));
    if (appliedPulses <= 0) {
      return;
    }
    if (previousPulseAt == null) {
      HudStatus.duration(playerId, this, now, budget);
    }

    long remaining = budget - (appliedPulses * PULSE_MILLIS);
    airBudgetMillis.put(playerId, remaining);
    int pulseIndex = (int) ((maximum - remaining) / PULSE_MILLIS);
    float driftPitch = maximum <= 0
        ? 1.8F
        : (float) (1.2D + (0.6D * (1D - ((double) remaining / (double) maximum))));

    applyFallEffect(player);
    emitPulseFx(player, firstPulse, remaining < PULSE_MILLIS, pulseIndex, driftPitch);

    addStat(player, "chronos.pocket-watch.slow-fall-seconds", appliedPulses * (PULSE_MILLIS / 1000D));
    xpSilent(player, appliedPulses * getConfig().xpPerPulse, "chronos:pocket-watch");
  }

  private void emitPulseFx(PlayerRef player, boolean activate, boolean deplete, int pulseIndex, float driftPitch) {
    Loc feet = player.location();
    if (activate) {
      fx(feet, FxPriority.TRANSITION)
          .burst(Particles.CLOUD, 6, 0.2D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.2F);
      return;
    }
    if (deplete) {
      fx(feet, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.2D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
      return;
    }

    FxEmitter drift = fx(feet, FxPriority.TRAIL)
        .particle(Particles.CLOUD, 2, 0, 0.1D, 0, 0.1D, 0.01D);
    if ((pulseIndex & 3) == 0) {
      drift.particle(Particles.END_ROD, 1, 0, 0.2D, 0, 0.05D, 0.02D);
    }
    if ((pulseIndex & 1) == 0) {
      drift.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.15F, driftPitch);
    }
  }

  private record FallEffect(EffectInstance previous, int previousStartedAt) {
  }

  @ConfigDescription("Sneak while airborne to fall in slow motion, with a level scaled time budget per airtime.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base seconds of slow falling sustainable per airtime.", impact = "Higher values let the player drift longer each fall.")
    double baseBudgetSeconds = 2;
    @ConfigDoc(value = "Extra sustain seconds per adaptation level.", impact = "Higher values make leveling extend the drift budget faster.")
    double budgetSecondsPerLevel = 1;
    @ConfigDoc(value = "Duration in ticks of each refreshing slow falling pulse.", impact = "Higher values leave slow falling lingering longer after sneaking stops.")
    int pulseDurationTicks = 15;
    @ConfigDoc(value = "Minimum fall distance in blocks before the slow fall kicks in.", impact = "Lower values start the slow fall earlier in the drop.")
    double minFallDistance = 1.5;
    @ConfigDoc(value = "Requires a clock anywhere in the inventory for the slow fall to apply.", impact = "False removes the item requirement entirely.")
    boolean requireClock = true;
    @ConfigDoc(value = "XP granted per slow fall pulse.", impact = "Higher values grant more skill XP while drifting.")
    double xpPerPulse = 0.3;
    @ConfigDoc(value = "Maximum learned players processed per pulse pass.", impact = "Higher values refresh more players immediately; lower values spread owner-thread scheduling across passes.")
    int maxPlayersPerPass = 512;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
