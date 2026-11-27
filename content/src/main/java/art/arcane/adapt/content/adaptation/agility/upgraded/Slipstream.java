package art.arcane.adapt.content.adaptation.agility.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.agility.AgilityMarathoner;
import art.arcane.adapt.content.adaptation.agility.AgilityRunRamp;
import art.arcane.adapt.content.adaptation.agility.AgilitySlide;
import art.arcane.adapt.content.adaptation.agility.AgilitySlipstreamSlide;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Wind Up, Slipstream Slide and Marathoner fused: running keeps adding speed up to a ceiling that rises with level, a
 * sprint-sneak slide carries the run through instead of ending it, and sprinting drains no hunger. Featherfoot runs at
 * {@link #featherfootLevel} for the holder; Vault is kept at its only level by every Agility mutation.
 */
public class Slipstream extends SkillUpgrade<Slipstream.Config> {
  public static final Id ID = Id.adapt("agility/upgrade/slipstream");

  private final AgilityRunRamp runRamp = new AgilityRunRamp(this, playerState(),
      (PlayerRef player, double ticks) -> addStat(player, "agility.slipstream.full-speed-ticks", ticks));
  private final AgilitySlide slide = new AgilitySlide(this);
  private final Cooldowns slideCooldown = cooldowns();
  private final Cooldowns lastSprint = cooldowns();
  private final Cooldowns slideCarry = cooldowns();

  public Slipstream() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("blue_ice"));
    setInterval(50);
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, AgilitySlipstreamSlide.CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    clear(player.player());
  }

  @Override
  public void unregister() {
    super.unregister();
    slide.shutdown();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getSpeedPerStep(level), 0), 1);
    statLore(v, Format.f(getSlideForce(level) * 20D, 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getSlideCooldownMillis(level), 1), 3);
    statLore(v, Format.pc(getMaximumSpeedBonus(level), 0), 4);
    statLore(v, featherfootLevel(level), 5);
  }

  /** The level Featherfoot runs at for a holder of this upgrade at {@code level}: its top level at this one's top. */
  public int featherfootLevel(int level) {
    return level <= 0 ? 0 : getConfig().featherfootLevelBase + level;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (p.isSprinting()) {
      lastSprint.mark(id);
    }

    if (!p.isSprinting() && !runRamp.tracking(id)) {
      return;
    }

    int level = getActiveLevel(p);
    boolean running = p.isSprinting() || carried(id, level);
    if (!running || level <= 0 || !AgilityRunRamp.eligible(p, true)) {
      runRamp.clear(p);
      return;
    }

    runRamp.update(p, ramp(level), System.currentTimeMillis());
  }

  @Handle
  public void on(PlayerToggleSprintEvent e) {
    PlayerRef p = e.player();
    if (!e.isSprinting() && !carried(p.uuid(), getActiveLevel(p))) {
      runRamp.clear(p);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking()) {
      if (preference(p, AgilitySlipstreamSlide.CONTROL) == AgilitySlipstreamSlide.Control.HOLD) {
        slide.stop(p);
      }
      return;
    }

    withAdaptedPlayer(p, e, () -> attemptSlide(p));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityExhaustionEvent e) {
    PlayerRef p = e.player();
    if (e.exhaustion() <= 0F || !AgilityMarathoner.isSprintExhaustion(e.reason(), p.isSprinting())
        || getActiveLevel(p) <= 0) {
      return;
    }

    addStat(p, "agility.slipstream.hunger-saved", e.exhaustion());
    e.setExhaustion(0F);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    clear(e.player());
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    clear(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    slide.stop(e.player());
  }

  private void attemptSlide(PlayerRef p) {
    UUID id = p.uuid();
    if (!slide.accepting() || !AgilitySlide.eligible(p, lastSprint.remaining(id, 350L) > 0)) {
      return;
    }

    int level = getActiveLevel(p);
    long cooldownMillis = getSlideCooldownMillis(level);
    if (level <= 0 || !slideCooldown.isReady(id, cooldownMillis)) {
      return;
    }

    Vec3 direction = p.location().direction().withY(0);
    if (direction.lengthSquared() <= 1.0E-4D) {
      return;
    }

    slideCooldown.mark(id, cooldownMillis);
    int slideTicks = getSlideTicks(level);
    slideCarry.mark(id);
    slide.launch(p, direction.normalize(), getSlideForce(level), slideTicks,
        getConfig().slideFrictionReduction);
    cue(p, MutationCue.BOON);
    LingeringSound lingering = linger(p);
    AdaptApi.scheduler().runEntity(p, lingering::stop, slideTicks);
    addStat(p, "agility.slipstream.slides", 1);
  }

  private boolean carried(UUID id, int level) {
    return slide.sliding(id) || slideCarry.remaining(id, (getSlideTicks(level) * 50L) + getConfig().carryGraceMillis) > 0;
  }

  private void clear(PlayerRef p) {
    runRamp.clear(p);
    slide.stop(p);
  }

  AgilityRunRamp.Ramp ramp(int level) {
    double speedPerStep = getSpeedPerStep(level);
    return new AgilityRunRamp.Ramp(getConfig().ticksPerStep, false,
        (double progress) -> Math.min(getMaximumSpeedBonus(level), progress * speedPerStep), getConfig().speedLerpPerTick, getConfig().movementVelocityThreshold);
  }

  double getMaximumSpeedBonus(int level) {
    return getConfig().maximumSpeedBonusBase + (level * getConfig().maximumSpeedBonusPerLevel);
  }

  double getSpeedPerStep(int level) {
    return getConfig().speedPerStepBase + (level * getConfig().speedPerStepPerLevel);
  }

  double getSlideForce(int level) {
    return getConfig().slideForceBase + (level * getConfig().slideForcePerLevel);
  }

  long getSlideCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumSlideCooldownMillis,
        getConfig().slideCooldownMillisBase - (level * getConfig().slideCooldownMillisPerLevel));
  }

  int getSlideTicks(int level) {
    return Math.max(3, getConfig().slideTicksBase + (level * getConfig().slideTicksPerLevel));
  }

  @ConfigDescription("Running keeps adding speed up to a ceiling, slides carry the run, sprinting drains no hunger, and Featherfoot comes along.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Running ticks that make up one step of the speed ramp.", impact = "Lower values make speed climb faster.")
    double ticksPerStep = 100;
    @ConfigDoc(value = "Relative movement speed gained per step before level scaling.", impact = "Higher values make every step of running faster.")
    double speedPerStepBase = 0.10;
    @ConfigDoc(value = "Relative movement speed per step added per level.", impact = "Higher values reward higher levels more.")
    double speedPerStepPerLevel = 0.05;
    @ConfigDoc(value = "Share of the gap to the target speed closed each tick.", impact = "Higher values make the speed follow the ramp more tightly.")
    double speedLerpPerTick = 0.45;
    @ConfigDoc(value = "Horizontal speed in blocks per tick needed to count as running at full speed.", impact = "Higher values need clearer movement before the full-speed trail and stat.")
    double movementVelocityThreshold = 0.015;
    @ConfigDoc(value = "Slide velocity in blocks per tick before level scaling.", impact = "Higher values throw the slide further.")
    double slideForceBase = 0.6;
    @ConfigDoc(value = "Slide velocity in blocks per tick added per level.", impact = "Higher values reward higher levels more.")
    double slideForcePerLevel = 0.15;
    @ConfigDoc(value = "Prone ticks of a slide before level scaling.", impact = "Higher values keep the slide going longer.")
    int slideTicksBase = 14;
    @ConfigDoc(value = "Prone ticks added per level.", impact = "Higher values lengthen slides at higher levels.")
    int slideTicksPerLevel = 4;
    @ConfigDoc(value = "Fraction of ground friction removed during a slide.", impact = "Higher values keep more momentum while prone.")
    double slideFrictionReduction = 0.9;
    @ConfigDoc(value = "Slide cooldown before level scaling, in milliseconds.", impact = "Higher values make slides rarer.")
    double slideCooldownMillisBase = 3000;
    @ConfigDoc(value = "Slide cooldown removed per level, in milliseconds.", impact = "Higher values make higher levels slide more often.")
    double slideCooldownMillisPerLevel = 600;
    @ConfigDoc(value = "Shortest slide cooldown at any level, in milliseconds.", impact = "Lower values allow slides back to back.")
    double minimumSlideCooldownMillis = 900;
    @ConfigDoc(value = "Time after a slide ends during which the run still counts without sprinting, in milliseconds.", impact = "Higher values give more time to start sprinting again after a slide.")
    long carryGraceMillis = 750;
    @ConfigDoc(value = "Highest relative movement speed a run can reach before level scaling.", impact = "Higher values let long runs go faster; very high values outrun chunk loading and anti-cheat.")
    double maximumSpeedBonusBase = 0.4;
    @ConfigDoc(value = "Highest relative movement speed added per level.", impact = "Higher values reward higher levels more.")
    double maximumSpeedBonusPerLevel = 0.2;
    @ConfigDoc(value = "Featherfoot level at upgrade level 0; each upgrade level adds one.", impact = "Higher values unlock more Featherfoot surfaces sooner.")
    int featherfootLevelBase = 1;
  }
}
