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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AgilitySlipstreamSlide extends SimpleAdaptation<AgilitySlipstreamSlide.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", AgilityMessages.AGILITYSLIPSTREAMSLIDE_PREFERENCE_CONTROL, Control.TAP, List.of(
          new PlayerPreference.Choice<>(Control.TAP, AgilityMessages.AGILITYSLIPSTREAMSLIDE_PREFERENCE_CONTROL_TAP, Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Control.HOLD, AgilityMessages.AGILITYSLIPSTREAMSLIDE_PREFERENCE_CONTROL_HOLD, Id.minecraft("leather_boots"), 1))));
  public static final PlayerPreference<CommonPreferences.Scale> DURATION = CommonPreferences.scale("duration", AgilityMessages.AGILITYSLIPSTREAMSLIDE_PREFERENCE_DURATION);

  private static final int FRICTION_CLEANUP_GRACE_TICKS = 2;
  private static final double LEGACY_SLIDE_TICKS_BASE = 7D;
  private static final double LEGACY_SLIDE_TICKS_FACTOR = 5D;
  private static final double DEFAULT_SLIDE_TICKS_BASE = 14D;
  private static final double DEFAULT_SLIDE_TICKS_FACTOR = 10D;
  private static final String FRICTION_SLOT = "slide-friction";

  private final Cooldowns slideCooldown = cooldowns();
  private final Cooldowns lastSprint = cooldowns();
  private final Map<UUID, ForcedPoseState> forcedPoses = new ConcurrentHashMap<>();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean(false);

  public AgilitySlipstreamSlide() {
    super("agility-slipstream-slide");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ice"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("packed_ice"))
        .key("challenge_agility_slipstream_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("blue_ice"))
            .key("challenge_agility_slipstream_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_slipstream_500", "agility.slipstream-slide.slides", 500, 400);
    registerMilestone("challenge_agility_slipstream_5k", "agility.slipstream-slide.slides", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL, DURATION);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    stopForcedPose(player.getPlayer());
  }

  @Override
  public void unregister() {
    if (!lifecycleCleanupStarted.compareAndSet(false, true)) {
      super.unregister();
      return;
    }
    super.unregister();
    ForcedPoseState[] states = forcedPoses.values().toArray(new ForcedPoseState[0]);
    for (ForcedPoseState state : states) {
      if (forcedPoses.remove(state.owner.uuid(), state)) {
        dispatchPoseRestore(state);
      }
    }
    forcedPoses.clear();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getSlideForce(level) * 20D, 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.LIGHT_PURPLE + " " + AdaptLanguage.text(AgilityMessages.SLIPSTREAM_SLIDE_LORE3));
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    if (usesLegacySlideDuration(loadedConfig.slideTicksBase, loadedConfig.slideTicksFactor)) {
      loadedConfig.slideTicksBase = DEFAULT_SLIDE_TICKS_BASE;
      loadedConfig.slideTicksFactor = DEFAULT_SLIDE_TICKS_FACTOR;
    }
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (p.isSprinting()) {
      lastSprint.mark(p.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      if (preference(e.player(), CONTROL) == Control.HOLD) {
        stopForcedPose(e.player());
      }
      return;
    }

    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> attemptSlide(p));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    stopForcedPose(p);
    lastSprint.clear(id);
    slideCooldown.clear(id);
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    stopForcedPose(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    stopForcedPose(e.player());
  }

  private void attemptSlide(PlayerRef p) {
    if (lifecycleCleanupStarted.get() || !isSlideEligible(p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.uuid();
    if (!slideCooldown.isReady(id, getCooldownMillis(level))) {
      return;
    }

    if (p.foodLevel() <= 0) {
      return;
    }

    Loc location = p.location();
    Vec3 direction = location.direction().withY(0);
    if (direction.lengthSquared() <= 1.0E-4D) {
      return;
    }

    direction = direction.normalize();
    slideCooldown.mark(id);
    applyHungerCost(p, getConfig().hungerCost);

    double force = getSlideForce(level);
    Vec3 velocity = direction.multiply(force).withY(Math.min(p.velocity().y(), -0.04D));
    p.setVelocity(velocity);
    startProne(p, Math.max(3, (int) (getSlideTicks(level) * preference(p, DURATION).multiplier())), getSlideFrictionReduction(), Attributes.FRICTION_MODIFIER);

    Loc at = p.location();
    fx(at, FxPriority.GAMEPLAY)
        .trail(Particles.CLOUD, -velocity.x(), 0.05D, -velocity.z(), 1.0D, 6)
        .particle(Particles.blockCrack(at.world().blockAt(at).relative(BlockFace.DOWN).state()), 6, 0, 0.1D, 0, 0.3D, 0.05D)
        .chord(Sounds.BLOCK_LADDER_STEP, 0.6F, 0.6F, Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.5F);

    addStat(p, "agility.slipstream-slide.slides", 1);
    xp(p, getConfig().xpPerSlide);

    if (level >= getMaxLevel()) {
      runSlideSlow(p, getSlideTicks(level), getSlowDurationTicks(), slowAmount(getConfig().slowAmplifier));
    }
  }

  private void startProne(PlayerRef p, int proneTicks, double frictionReduction, Id frictionAttribute) {
    if (lifecycleCleanupStarted.get()) {
      return;
    }

    stopForcedPose(p);
    long until = System.currentTimeMillis() + (proneTicks * 50L);
    double reduction = slideFrictionReduction(frictionReduction);
    boolean frictionApplied = frictionAttribute != null && reduction > 0D;
    ForcedPoseState state = new ForcedPoseState(p, until, p.pose(), p.hasFixedPose(), frictionAttribute, frictionApplied);
    forcedPoses.put(p.uuid(), state);
    if (lifecycleCleanupStarted.get()) {
      forcedPoses.remove(p.uuid(), state);
      return;
    }

    try {
      p.setPose(Pose.SWIMMING, true);
      if (frictionApplied) {
        AdaptAttributeService.get().applyTimed(p, getName(), FRICTION_SLOT, frictionAttribute,
            -reduction, AttributeOperation.ADD_MULTIPLIED_TOTAL, proneTicks + FRICTION_CLEANUP_GRACE_TICKS);
      }
      if (lifecycleCleanupStarted.get() || forcedPoses.get(p.uuid()) != state) {
        forcedPoses.remove(p.uuid(), state);
        restorePose(state);
        return;
      }
      scheduleProneMaintenance(state);
    } catch (RuntimeException | Error error) {
      forcedPoses.remove(p.uuid(), state);
      restorePose(state);
      throw error;
    }
  }

  private void maintainProne(ForcedPoseState state) {
    PlayerRef p = state.owner;
    UUID id = p.uuid();
    if (forcedPoses.get(id) != state) {
      return;
    }

    if (!p.isOnline() || p.isDead() || !hasActiveAdaptation(p) || isSlideInterrupted(p)
        || (preference(p, CONTROL) == Control.HOLD && !p.isSneaking())) {
      finishForcedPose(state);
      return;
    }

    if (isProneActive(state.untilMillis, System.currentTimeMillis())) {
      p.setPose(Pose.SWIMMING, true);
      scheduleProneMaintenance(state);
      return;
    }

    finishForcedPose(state);
  }

  private void scheduleProneMaintenance(ForcedPoseState state) {
    if (lifecycleCleanupStarted.get() || !Tasks.runEntity(state.owner, () -> runProneMaintenance(state), 1)) {
      finishForcedPose(state);
    }
  }

  private void runProneMaintenance(ForcedPoseState state) {
    try {
      maintainProne(state);
    } catch (RuntimeException | Error error) {
      finishForcedPose(state);
      throw error;
    }
  }

  private void finishForcedPose(ForcedPoseState state) {
    if (forcedPoses.remove(state.owner.uuid(), state)) {
      restorePose(state);
    }
  }

  private void stopForcedPose(PlayerRef p) {
    ForcedPoseState state = forcedPoses.remove(p.uuid());
    if (state != null) {
      restorePose(state);
    }
  }

  private void dispatchPoseRestore(ForcedPoseState state) {
    PlayerRef p = state.owner;
    Runnable restore = () -> restorePose(state);
    if (!Tasks.runEntity(p, restore) && canRestoreDirectly(p)) {
      restore.run();
    }
  }

  private void restorePose(ForcedPoseState state) {
    PlayerRef p = state.owner;
    if (state.frictionApplied) {
      AdaptAttributeService.get().remove(p, getName(), FRICTION_SLOT, state.frictionAttribute);
    }
    if (p.pose() != Pose.SWIMMING || !p.hasFixedPose()) {
      return;
    }
    if (p.isSwimming()) {
      p.setPose(Pose.SWIMMING, false);
      return;
    }
    p.setPose(state.previousPose, state.previousPoseFixed);
  }

  private boolean canRestoreDirectly(PlayerRef p) {
    return Tasks.isFoliaThreading() ? Tasks.isOwnedByCurrentRegion(p) : Tasks.isPrimaryThread();
  }

  private void runSlideSlow(PlayerRef p, int ticksLeft, int slowDurationTicks, double slowAmount) {
    if (ticksLeft <= 0 || slowDurationTicks <= 0 || !p.isOnline() || p.isDead()
        || !forcedPoses.containsKey(p.uuid()) || !isPlayerEnabled(p)) {
      return;
    }

    for (EntityRef nearby : p.nearbyEntities(1.3D, 1.0D, 1.3D)) {
      if (nearby instanceof LivingRef target && !(nearby instanceof PlayerRef) && canDamageTarget(p, nearby)) {
        AdaptAttributeService.get().applyTimed(target, getName(), "slow", Attributes.MOVEMENT_SPEED,
            slowAmount, AttributeOperation.ADD_MULTIPLIED_TOTAL, slowDurationTicks);
      }
    }

    Tasks.runEntity(p, () -> runSlideSlow(p, ticksLeft - 1, slowDurationTicks, slowAmount), 1);
  }

  private void applyHungerCost(PlayerRef p, double cost) {
    double saturation = p.saturation();
    if (saturation >= cost) {
      p.setSaturation((float) (saturation - cost));
      return;
    }

    p.setSaturation(0F);
    int foodCost = (int) Math.ceil(cost - saturation);
    p.setFoodLevel(Math.max(0, p.foodLevel() - foodCost));
  }

  private boolean isSlideEligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    boolean sprinting = p.isSprinting() || lastSprint.remaining(p.uuid(), 350L) > 0;
    return sprinting
        && p.isOnGround()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && p.vehicle() == null;
  }

  private boolean isSlideInterrupted(PlayerRef p) {
    GameMode mode = p.gameMode();
    return (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE)
        || p.isSwimming()
        || p.isFlying()
        || p.isGliding()
        || p.vehicle() != null;
  }

  private double getSlideForce(int level) {
    return getConfig().slideForceBase + (getLevelPercent(level) * getConfig().slideForceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(getConfig().cooldownMillisFloor,
        Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisReduction)));
  }

  private int getSlideTicks(int level) {
    return Math.max(3, (int) Math.round(getConfig().slideTicksBase + (getLevelPercent(level) * getConfig().slideTicksFactor)));
  }

  private double getSlideFrictionReduction() {
    return slideFrictionReduction(getConfig().slideFrictionReduction);
  }

  private int getSlowDurationTicks() {
    return slowDurationTicks(getConfig().slowDurationTicks);
  }

  static int slowDurationTicks(int configuredTicks) {
    return Math.max(5, configuredTicks);
  }

  static double slowAmount(int amplifier) {
    return Math.max(-1.0D, -0.15D * (Math.max(0, amplifier) + 1));
  }

  static boolean isProneActive(long untilMillis, long nowMillis) {
    return untilMillis > nowMillis;
  }

  static boolean usesLegacySlideDuration(double baseTicks, double factorTicks) {
    return Double.compare(baseTicks, LEGACY_SLIDE_TICKS_BASE) == 0
        && Double.compare(factorTicks, LEGACY_SLIDE_TICKS_FACTOR) == 0;
  }

  static double slideFrictionReduction(double configuredReduction) {
    if (!Double.isFinite(configuredReduction)) {
      return 0D;
    }
    return Math.max(0D, Math.min(1D, configuredReduction));
  }

  @ConfigDescription("Tap sneak while sprinting to enter a fixed prone pose and slide with reduced ground friction, keeping momentum under 1-block gaps.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base horizontal slide velocity in blocks per tick before level scaling.", impact = "Higher values launch a longer slide; lower values keep it short.")
    double slideForceBase = 0.5;
    @ConfigDoc(value = "Additional slide velocity in blocks per tick granted at max level.", impact = "Higher values widen the slide-distance gain from leveling.")
    double slideForceFactor = 0.45;
    @ConfigDoc(value = "Cooldown between slides in milliseconds at level 0 before scaling.", impact = "Higher values slow slide frequency across all levels.")
    double cooldownMillisBase = 4000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds applied at max level.", impact = "Higher values make leveling shorten the slide cooldown more.")
    double cooldownMillisReduction = 2500;
    @ConfigDoc(value = "Minimum slide cooldown in milliseconds after all reductions.", impact = "Higher values keep a hard floor under slide spam.")
    long cooldownMillisFloor = 1300;
    @ConfigDoc(value = "Base slide prone-pose duration in ticks before level scaling.", impact = "Higher values keep the crawl pose longer per slide.")
    double slideTicksBase = DEFAULT_SLIDE_TICKS_BASE;
    @ConfigDoc(value = "Additional slide prone-pose duration in ticks granted at max level.", impact = "Higher values extend the low-pose window when leveled.")
    double slideTicksFactor = DEFAULT_SLIDE_TICKS_FACTOR;
    @ConfigDoc(value = "Fraction of ground friction removed while the slide is active.", impact = "Higher values preserve more horizontal momentum during the prone slide.")
    double slideFrictionReduction = 0.9;
    @ConfigDoc(value = "Saturation/hunger cost paid per slide.", impact = "Higher values make sliding drain stamina faster.")
    double hungerCost = 1.8;
    @ConfigDoc(value = "Slowness amplifier applied to mobs slid through at max level.", impact = "Higher values slow struck mobs harder; only active at max level.")
    int slowAmplifier = 1;
    @ConfigDoc(value = "Duration in ticks of the max-level slow applied to mobs slid through.", impact = "Higher values keep slid mobs slowed longer.")
    int slowDurationTicks = 40;
    @ConfigDoc(value = "Experience granted per successful slide.", impact = "Higher values reward sliding with more skill xp.")
    double xpPerSlide = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 4;
    }
  }

  private static final class ForcedPoseState {
    private final PlayerRef owner;
    private final long untilMillis;
    private final Pose previousPose;
    private final boolean previousPoseFixed;
    private final Id frictionAttribute;
    private final boolean frictionApplied;

    private ForcedPoseState(PlayerRef owner, long untilMillis, Pose previousPose, boolean previousPoseFixed, Id frictionAttribute, boolean frictionApplied) {
      this.owner = owner;
      this.untilMillis = untilMillis;
      this.previousPose = previousPose;
      this.previousPoseFixed = previousPoseFixed;
      this.frictionAttribute = frictionAttribute;
      this.frictionApplied = frictionApplied;
    }
  }

  public enum Control { TAP, HOLD }
}
