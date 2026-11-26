package art.arcane.adapt.content.adaptation.hunter.upgraded;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.adaptation.VelocityBurstRuntime;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.hunter.HunterPreferences;
import art.arcane.adapt.content.adaptation.hunter.HunterSpeedBurst;
import art.arcane.adapt.content.adaptation.hunter.HunterStrengthBuff;
import art.arcane.adapt.content.adaptation.hunter.HunterStruck;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adrenaline, Hunter's Strength, Hunter's Speed and Hunter's Regen fused: being struck grants all three buffs at once
 * for a single hunger cost, and the lower your health the stronger and longer they are.
 */
public class BloodFrenzy extends SkillUpgrade<BloodFrenzy.Config> {
  private static final Rgb FRENZY_RED = new Rgb(170, 20, 20);
  private final HunterStrengthBuff strength = new HunterStrengthBuff(this, playerState());
  private final HunterSpeedBurst speed = new HunterSpeedBurst(this, playerState());
  private final Map<UUID, LingeringSound> lingering = playerState();
  private final Cooldowns fizzleCooldown = cooldowns();

  public BloodFrenzy() {
    super("upgraded-hunter-blood-frenzy");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("red_dye"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.RESERVE);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (getActiveLevel(player.getPlayer()) <= 0) {
      strength.remove(player.getPlayer());
      speed.stop(player.getPlayer());
    }
  }

  @Override
  public void unregister() {
    speed.unregister();
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getStrengthTicks(level, 1D) * 50L, 1), 1);
    statLore(v, Form.pc(getLowHealthBoost(level), 0), 2);
    statLore(v, Form.duration(getConfig().hungerDurationTicks * level * 50L, 1), 3);
  }

  @Handle
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !isAdaptableDamageCause(e)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || HunterStruck.suppressed(this, p)) {
      return;
    }

    if (p.foodLevel() == 0) {
      addPotionStacks(p, HunterStruck.POISON, getConfig().poisonAmplifierBase - level,
          getConfig().poisonDurationTicks, false);
      if (fizzleCooldown.isReady(p.uuid(), 1200L)) {
        fizzleCooldown.mark(p.uuid());
        FxPresets.failFizzle(this, p);
      }
      return;
    }

    double scale = getScale(level, p.health() / p.maxHealth());
    int strengthTicks = getStrengthTicks(level, scale);
    int speedTicks = scaledTicks(getConfig().speedTicksPerLevel, scale) * level;
    int regenTicks = scaledTicks(getConfig().regenTicksPerLevel, scale) * level;
    boolean strong = strength.apply(p, level, scaledTicks(getConfig().strengthTicksPerLevel, scale), false,
        HunterStrengthBuff.strengthDamageBonus(level) * scale);
    boolean fast = speed.grant(p, level, speedTicks, false, burstProfile());
    boolean healing = HunterStruck.regenerate(this, p, level, regenTicks, false);
    if (!strong && !fast && !healing) {
      return;
    }

    addPotionStacks(p, HunterStruck.HUNGER, getConfig().hungerAmplifierBase - level,
        getConfig().hungerDurationTicks * level, false);
    addStat(p, "hunter.blood-frenzy.frenzies", 1);
    cue(p, MutationCue.BOON);
    startLinger(p, Math.max(strengthTicks, Math.max(speedTicks, regenTicks)));
    fx(p.eyeLocation().add(0, -0.4D, 0), FxPriority.COMBAT)
        .dustBurst(FRENZY_RED, 10, 0.35D, 1.2F)
        .particle(Particles.HEART, 3, 0, 0.3D, 0, 0.4D, 0);
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    UUID id = e.player().uuid();
    strength.forget(id);
    LingeringSound sound = lingering.remove(id);
    if (sound != null) {
      sound.stop();
    }
  }

  private void startLinger(PlayerRef p, int durationTicks) {
    LingeringSound sound = linger(p);
    LingeringSound previous = lingering.put(p.uuid(), sound);
    if (previous != null) {
      previous.stop();
    }
    Tasks.runEntity(p, () -> {
      lingering.remove(p.uuid(), sound);
      sound.stop();
    }, Math.max(1, durationTicks));
  }

  private VelocityBurstRuntime.Profile burstProfile() {
    Config config = getConfig();
    return new VelocityBurstRuntime.Profile(config.baseHorizontalSpeed, config.maxHorizontalSpeed, config.accelPerTick,
        config.brakePerTick, config.stopThreshold, config.hardStopOnInvalidState, config.fallbackInputVelocityThreshold);
  }

  double getScale(int level, double healthFraction) {
    return 1D + HunterStruck.lowHealthBonus(getLowHealthBoost(level), healthFraction);
  }

  double getLowHealthBoost(int level) {
    return getConfig().lowHealthBoostBase + (level * getConfig().lowHealthBoostPerLevel);
  }

  int getStrengthTicks(int level, double scale) {
    return scaledTicks(getConfig().strengthTicksPerLevel, scale) * level;
  }

  private static int scaledTicks(int ticks, double scale) {
    return (int) Math.round(ticks * scale);
  }

  @ConfigDescription("Being struck grants strength, speed and regeneration together for one hunger cost, stronger at low health.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Strength duration per level at full health, in ticks.", impact = "Higher values keep the extra attack damage longer.")
    int strengthTicksPerLevel = 40;
    @ConfigDoc(value = "Speed burst duration per level at full health, in ticks.", impact = "Higher values keep the speed burst longer.")
    int speedTicksPerLevel = 60;
    @ConfigDoc(value = "Regeneration duration per level at full health, in ticks.", impact = "Higher values heal for longer.")
    int regenTicksPerLevel = 40;
    @ConfigDoc(value = "Boost to strength and every duration near death, before level scaling.", impact = "Higher values make the frenzy grow more as health drops.")
    double lowHealthBoostBase = 0.5;
    @ConfigDoc(value = "Near-death boost added per level.", impact = "Higher values reward higher levels more.")
    double lowHealthBoostPerLevel = 0.25;
    @ConfigDoc(value = "Hunger amplifier before subtracting the level.", impact = "Higher values make the hunger cost harsher.")
    int hungerAmplifierBase = 10;
    @ConfigDoc(value = "Hunger duration per level for each frenzy, in ticks.", impact = "Higher values make each frenzy cost more food.")
    int hungerDurationTicks = 50;
    @ConfigDoc(value = "Poison amplifier before subtracting the level, applied when struck with no food left.", impact = "Higher values punish frenzying while starving.")
    int poisonAmplifierBase = 6;
    @ConfigDoc(value = "Poison duration when struck with no food left, in ticks.", impact = "Higher values punish frenzying while starving.")
    int poisonDurationTicks = 50;
    @ConfigDoc(value = "Base horizontal speed of the burst before amplifier scaling.", impact = "Higher values move faster during the burst.")
    double baseHorizontalSpeed = 0.13;
    @ConfigDoc(value = "Highest horizontal speed the burst can force.", impact = "Caps runaway momentum.")
    double maxHorizontalSpeed = 0.32;
    @ConfigDoc(value = "How fast the burst accelerates toward its target speed per tick.", impact = "Higher values accelerate faster.")
    double accelPerTick = 0.045;
    @ConfigDoc(value = "How fast burst velocity decays once movement input stops.", impact = "Higher values stop sooner.")
    double brakePerTick = 0.08;
    @ConfigDoc(value = "Horizontal velocity treated as fully stopped.", impact = "Higher values stop sooner.")
    double stopThreshold = 0.01;
    @ConfigDoc(value = "Clear burst velocity when the player enters an invalid state.", impact = "Prevents retained speed when state changes skip the normal flow.")
    boolean hardStopOnInvalidState = true;
    @ConfigDoc(value = "Movement threshold used when direct input is unavailable.", impact = "Only used on runtimes without player input access.")
    double fallbackInputVelocityThreshold = 0.0008;
  }
}
