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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.RunsWithoutLearnedAdaptation;
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
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.api.world.BlockTagSet;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class AgilityLadderSlide extends SimpleAdaptation<AgilityLadderSlide.Config> {
  public static final Id ID = Id.adapt("agility/ladder_slide");
  public static final PlayerPreference<CommonPreferences.Toggle> ASCENT = CommonPreferences.toggle("ascent", Text.key("adapt.agilityladderslide.preferences.preference_ascent"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DESCENT = CommonPreferences.toggle("descent", Text.key("adapt.agilityladderslide.preferences.preference_descent"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> SPEED = CommonPreferences.scale("speed", Text.key("adapt.agilityladderslide.preferences.preference_speed"));
  public static final PlayerPreference<Sensitivity> SENSITIVITY = PlayerPreference.of("sensitivity", Sensitivity.class, Sensitivity.NORMAL, List.of(
          new PlayerPreference.Choice<>(Sensitivity.NORMAL, Text.key("adapt.agilityladderslide.preferences.preference_sensitivity_normal"), Id.minecraft("spyglass"), 1),
          new PlayerPreference.Choice<>(Sensitivity.PRECISE, Text.key("adapt.agilityladderslide.preferences.preference_sensitivity_precise"), Id.minecraft("target"), 1),
          new PlayerPreference.Choice<>(Sensitivity.RESPONSIVE, Text.key("adapt.agilityladderslide.preferences.preference_sensitivity_responsive"), Id.minecraft("feather"), 1))).labelled(Text.key("adapt.agilityladderslide.preferences.preference_sensitivity"));

  private static final double TICKS_PER_SECOND = 20.0D;
  private static final int COLUMN_END_BUFFER_BLOCKS = 2;
  private static final long SLIDE_GRACE_MS = 700L;
  private static final double MOVEMENT_EPSILON = 1.0E-4D;
  private static final double MAX_VERTICAL_SPEED = 1.0D;
  private static final double MIN_LOOK_ACTIVATION = 1.0D;
  private static final double MAX_LOOK_ANGLE = 89.0D;
  private static final double LEGACY_LOOK_ACTIVATION = 20.0D;
  private static final double LEGACY_LOOK_RELEASE = 10.0D;
  private static final double DEFAULT_LOOK_ACTIVATION = 30.0D;
  private static final double DEFAULT_LOOK_RELEASE = 15.0D;
  private static final Id SCAFFOLDING = Id.minecraft("scaffolding");
  private static final Predicate<PlayerRef> GROUNDED_ACTIONS = player -> !player.isFlying() && !player.isGliding() && !player.isSwimming();

  private final Map<UUID, ControlSession> controlSessions = playerState();
  private final Cooldowns slideGrace = cooldowns();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean(false);
  private final AtomicLong clientTagGeneration = new AtomicLong(0L);
  private final BlockTagSet climbable = new BlockTagSet(Id.minecraft("climbable"));

  public AgilityLadderSlide() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ladder"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ladder"))
        .key("challenge_agility_ladder_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_chain"))
            .key("challenge_agility_ladder_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_ladder_500", "agility.ladder-slide.blocks-climbed", 500, 300);
    registerMilestone("challenge_agility_ladder_10k", "agility.ladder-slide.blocks-climbed", 10000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, ASCENT, DESCENT, SPEED, SENSITIVITY);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    clearPlayerState(player.player(), false);
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      ControlSession[] sessions = controlSessions.values().toArray(new ControlSession[0]);
      controlSessions.clear();
      for (ControlSession session : sessions) {
        retireControlSession(session);
      }
    }
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDescentSpeed(level) * TICKS_PER_SECOND, 1), 1);
    statLore(v, Format.f(getClimbAssist(level) * TICKS_PER_SECOND, 1), 2);
    statLore(v, Format.f(getLookActivation(), 0) + " / " + Format.f(getLookRelease(), 0), 3);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.descentSpeedBase = normalizeSpeed(loadedConfig.descentSpeedBase);
    loadedConfig.descentSpeedPerLevel = normalizeSpeed(loadedConfig.descentSpeedPerLevel);
    loadedConfig.climbAssistBase = normalizeSpeed(loadedConfig.climbAssistBase);
    loadedConfig.climbAssistPerLevel = normalizeSpeed(loadedConfig.climbAssistPerLevel);
    if (Double.compare(loadedConfig.lookActivationDegrees, LEGACY_LOOK_ACTIVATION) == 0
        && Double.compare(loadedConfig.lookReleaseDegrees, LEGACY_LOOK_RELEASE) == 0) {
      loadedConfig.lookActivationDegrees = DEFAULT_LOOK_ACTIVATION;
      loadedConfig.lookReleaseDegrees = DEFAULT_LOOK_RELEASE;
    } else {
      loadedConfig.lookActivationDegrees = normalizeActivation(loadedConfig.lookActivationDegrees);
      loadedConfig.lookReleaseDegrees = normalizeRelease(
          loadedConfig.lookReleaseDegrees,
          loadedConfig.lookActivationDegrees
      );
    }
  }


  @Handle(priority = EventPriority.HIGHEST)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    if (e.to() == null) {
      return;
    }

    PlayerRef p = e.player();
    if (p.isClimbing()) {
      withPlayerThread(p, e, () -> startControl(p));
      return;
    }

    if (!controlSessions.isEmpty()) {
      clearPlayerState(p, false);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    PlayerRef p = e.player();
    ControlSession session = controlSessions.get(p.uuid());
    if (session == null) {
      return;
    }

    withPlayerThread(p, e, () -> haltControl(session));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    clearPlayerState(e.player(), true);
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    clearPlayerState(e.player(), true);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerGameModeChangeEvent e) {
    clearPlayerState(e.player(), true);
  }

  @Handle
  public void on(PlayerChangedWorldEvent e) {
    clearPlayerState(e.player(), true);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    clearPlayerState(e.player(), true);
  }

  @Handle
  public void on(ResourcesReloadedEvent e) {
    invalidateClientTagPackets();
  }

  void invalidateClientTagPackets() {
    climbable.invalidate();
    clientTagGeneration.incrementAndGet();
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.cause() != DamageCause.FALL) {
      return;
    }

    if (!getConfig().safeLanding || slideGrace.isReady(p.uuid(), SLIDE_GRACE_MS)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      slideGrace.clear(p.uuid());
      e.setCancelled(true);
      p.setFallDistance(0);
      fx(p.location(), FxPriority.TRANSITION)
          .burst(Particles.CLOUD, 4, 0.12D)
          .sound(Sounds.BLOCK_LADDER_STEP, 0.35F, 1.1F);
    });
  }

  private void startControl(PlayerRef p) {
    if (lifecycleCleanupStarted.get()) {
      return;
    }

    Loc location = p.location();
    BlockActionContext context = resolveInteractContext(p, location, GROUNDED_ACTIONS);
    BlockRef climbable = context == null ? null : activeClimbable(context.location());
    if (context == null || climbable == null) {
      clearPlayerState(p, false);
      return;
    }

    UUID playerId = p.uuid();
    ControlSession session = controlSessions.get(playerId);
    if (isInColumnEndBuffer(climbable)) {
      if (session != null) {
        finishControlInEndBuffer(session, context, climbable, location);
      }
      return;
    }
    if (session != null && session.worldId.equals(p.world().uid())) {
      scheduleControlTick(session);
      return;
    }
    if (session != null) {
      finishControlSession(session);
    }

    ControlSession created = new ControlSession(p, location);
    controlSessions.put(playerId, created);
    scheduleControlTick(created);
  }

  private void scheduleControlTick(ControlSession session) {
    if (lifecycleCleanupStarted.get()
        || controlSessions.get(session.owner.uuid()) != session
        || session.tickScheduled) {
      return;
    }

    session.tickScheduled = true;
    if (!AdaptApi.scheduler().runEntity(session.owner, () -> runControlTick(session), 1)) {
      session.tickScheduled = false;
      finishControlSession(session);
    }
  }

  private void runControlTick(ControlSession session) {
    session.tickScheduled = false;
    if (controlSessions.get(session.owner.uuid()) != session) {
      return;
    }

    try {
      maintainControl(session);
    } catch (RuntimeException | Error error) {
      finishControlSession(session);
      throw error;
    }
  }

  private void maintainControl(ControlSession session) {
    PlayerRef p = session.owner;
    if (!p.isOnline()
        || p.isDead()
        || !session.worldId.equals(p.world().uid())
        || !p.isClimbing()) {
      finishControlSession(session);
      return;
    }

    Loc current = p.location();
    BlockActionContext context = resolveInteractContext(p, current, GROUNDED_ACTIONS);
    BlockRef climbable = context == null ? null : activeClimbable(context.location());
    if (context == null || climbable == null) {
      finishControlSession(session);
      return;
    }

    if (isInColumnEndBuffer(climbable)) {
      finishControlInEndBuffer(session, context, climbable, current);
      return;
    }
    recordMovement(session, current);
    Mode mode = resolveMode(
        session.mode,
        current.pitch(),
        getLookActivation() * lookSensitivity(p),
        getLookRelease() * lookSensitivity(p),
        p.isSneaking()
    );
    if ((mode == Mode.CLIMB && !preferenceEnabled(p, ASCENT))
        || (mode == Mode.SLIDE && !preferenceEnabled(p, DESCENT))) {
      mode = Mode.NONE;
    }
    if (!applyMode(session, context.level(), climbable, mode)) {
      finishControlSession(session);
      return;
    }
    session.lastY = current.y();
    scheduleControlTick(session);
  }

  private void finishControlInEndBuffer(
      ControlSession session,
      BlockActionContext context,
      BlockRef climbable,
      Loc current
  ) {
    recordMovement(session, current);
    applyMode(session, context.level(), climbable, Mode.NONE);
    session.lastY = current.y();
    finishControlSession(session);
  }

  private void haltControl(ControlSession session) {
    PlayerRef p = session.owner;
    if (controlSessions.get(p.uuid()) != session || !p.isClimbing()) {
      return;
    }

    Loc current = p.location();
    BlockActionContext context = resolveInteractContext(p, current, GROUNDED_ACTIONS);
    BlockRef climbable = context == null ? null : activeClimbable(context.location());
    if (context == null || climbable == null) {
      finishControlSession(session);
      return;
    }

    recordMovement(session, current);
    if (!applyMode(session, context.level(), climbable, Mode.NONE)) {
      finishControlSession(session);
      return;
    }
    session.lastY = current.y();
    scheduleControlTick(session);
  }

  private boolean applyMode(ControlSession session, int level, BlockRef climbable, Mode mode) {
    PlayerRef p = session.owner;
    if (!ensureClientClimbingState(session, climbable.type(), mode == Mode.SLIDE)) {
      return false;
    }

    boolean changed = session.mode != mode;
    transition(p, session, mode);
    if (mode == Mode.NONE) {
      return !changed || sendVerticalMotion(p, 0D);
    }

    double targetY = targetVerticalVelocity(
        mode,
        getClimbAssist(level) * preference(p, SPEED).multiplier(),
        getDescentSpeed(level) * preference(p, SPEED).multiplier()
    );
    if (!sendVerticalMotion(p, targetY)) {
      return false;
    }

    p.setFallDistance(0);
    return true;
  }

  private boolean ensureClientClimbingState(ControlSession session, Id climbable, boolean suppressed) {
    long generation = clientTagGeneration.get();
    if (suppressed) {
      if (session.clientClimbingSuppressed.get()
          && climbable.equals(session.suppressedMaterial)
          && session.clientTagGeneration == generation) {
        return true;
      }
      if (!sendClientClimbingState(session.owner, climbable)) {
        return false;
      }
      session.clientClimbingSuppressed.set(true);
      session.suppressedMaterial = climbable;
      session.clientTagGeneration = generation;
      return true;
    }

    if (!session.clientClimbingSuppressed.get()) {
      return true;
    }
    if (!sendClientClimbingState(session.owner, null)) {
      return false;
    }
    session.clientClimbingSuppressed.set(false);
    session.suppressedMaterial = null;
    session.clientTagGeneration = generation;
    return true;
  }

  protected boolean sendVerticalMotion(PlayerRef p, double targetY) {
    return p.sendVerticalMotion(targetY);
  }

  protected boolean sendClientClimbingState(PlayerRef p, Id suppressedMaterial) {
    return p.sendClimbableTagOverride(suppressedMaterial);
  }

  private void recordMovement(ControlSession session, Loc current) {
    double deltaY = current.y() - session.lastY;
    if (session.mode == Mode.CLIMB && deltaY > MOVEMENT_EPSILON) {
      addStat(session.owner, "agility.ladder-slide.blocks-climbed", deltaY);
      maybeTrail(session.owner, false);
      return;
    }
    if (session.mode == Mode.SLIDE && deltaY < -MOVEMENT_EPSILON) {
      recordDescent(session.owner, -deltaY);
    }
  }

  private void recordDescent(PlayerRef p, double distance) {
    p.setFallDistance(0);
    if (getConfig().safeLanding) {
      slideGrace.mark(p.uuid());
    }
    addStat(p, "agility.ladder-slide.blocks-descended", distance);
    maybeTrail(p, true);
  }

  private void finishControlSession(ControlSession session) {
    if (controlSessions.remove(session.owner.uuid(), session)) {
      retireControlSession(session);
    }
  }

  private void retireControlSession(ControlSession session) {
    session.tickScheduled = false;
    if (!session.clientClimbingSuppressed.compareAndSet(true, false)) {
      return;
    }

    session.suppressedMaterial = null;
    try {
      sendClientClimbingState(session.owner, null);
    } catch (RuntimeException | Error error) {
      AdaptApi.log().error("AgilityLadderSlide failed", error);
    }
  }

  private void clearPlayerState(PlayerRef p, boolean clearLandingGrace) {
    ControlSession session = controlSessions.get(p.uuid());
    if (session != null) {
      finishControlSession(session);
    }
    if (clearLandingGrace) {
      slideGrace.clear(p.uuid());
    }
  }

  private void transition(PlayerRef p, ControlSession session, Mode to) {
    if (session.mode == to) {
      return;
    }

    session.mode = to;
    switch (to) {
      case CLIMB -> fx(p.location().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
          .burst(Particles.CRIT, 2, 0.05D)
          .sound(Sounds.ENTITY_PHANTOM_FLAP, 0.22F, 1.2F);
      case SLIDE -> fx(p.location(), FxPriority.TRANSITION)
          .burst(Particles.CLOUD, 3, 0.08D)
          .sound(Sounds.BLOCK_LADDER_STEP, 0.3F, 0.7F);
      case NONE -> {
      }
    }
  }

  private void maybeTrail(PlayerRef p, boolean sliding) {
    if (!(ThreadLocalRandom.current().nextDouble() < 0.08D)) {
      return;
    }

    double offsetY = sliding ? 0.2D : -0.2D;
    fx(p.location(), FxPriority.TRAIL).particle(Particles.CLOUD, 1, 0D, offsetY, 0D, 0.05D, 0.02D);
  }

  private BlockRef activeClimbable(Loc location) {
    BlockRef feet = location.world().blockAt(location);
    if (isControlledClimbable(feet.type())) {
      return feet;
    }

    BlockRef head = feet.relative(BlockFace.UP);
    if (isControlledClimbable(head.type())) {
      return head;
    }

    return null;
  }

  boolean isInColumnEndBuffer(BlockRef climbable) {
    return isInColumnEndBuffer(climbable, this::isControlledClimbable);
  }

  static boolean isInColumnEndBuffer(BlockRef climbable, Predicate<Id> climbableType) {
    return !hasClimbableRun(climbable, BlockFace.DOWN, COLUMN_END_BUFFER_BLOCKS, climbableType)
        || !hasClimbableRun(climbable, BlockFace.UP, COLUMN_END_BUFFER_BLOCKS, climbableType);
  }

  private static boolean hasClimbableRun(
      BlockRef climbable,
      BlockFace direction,
      int length,
      Predicate<Id> climbableType
  ) {
    BlockRef cursor = climbable;
    for (int i = 0; i < length; i++) {
      cursor = cursor.relative(direction);
      if (!climbableType.test(cursor.type())) {
        return false;
      }
    }
    return true;
  }

  private boolean isControlledClimbable(Id type) {
    return !SCAFFOLDING.equals(type) && climbable.contains(type);
  }

  static Mode resolveMode(Mode current, float pitch, double activationDegrees, double releaseDegrees) {
    if (!Float.isFinite(pitch)) {
      return Mode.NONE;
    }

    double activation = normalizeActivation(activationDegrees);
    double release = normalizeRelease(releaseDegrees, activation);
    if (pitch <= -activation) {
      return Mode.CLIMB;
    }
    if (pitch >= activation) {
      return Mode.SLIDE;
    }
    if (current == Mode.CLIMB && pitch < -release) {
      return Mode.CLIMB;
    }
    if (current == Mode.SLIDE && pitch > release) {
      return Mode.SLIDE;
    }
    return Mode.NONE;
  }

  static Mode resolveMode(
      Mode current,
      float pitch,
      double activationDegrees,
      double releaseDegrees,
      boolean sneaking
  ) {
    return sneaking
        ? Mode.NONE
        : resolveMode(current, pitch, activationDegrees, releaseDegrees);
  }

  static double targetVerticalVelocity(Mode mode, double climbSpeed, double descentSpeed) {
    return switch (mode) {
      case CLIMB -> normalizeSpeed(climbSpeed);
      case SLIDE -> -normalizeSpeed(descentSpeed);
      case NONE -> 0D;
    };
  }

  static double normalizeSpeed(double speed) {
    if (!Double.isFinite(speed) || speed <= 0D) {
      return 0D;
    }
    return Math.min(speed, MAX_VERTICAL_SPEED);
  }

  static double normalizeActivation(double activationDegrees) {
    if (!Double.isFinite(activationDegrees)) {
      return DEFAULT_LOOK_ACTIVATION;
    }
    return Math.max(MIN_LOOK_ACTIVATION, Math.min(activationDegrees, MAX_LOOK_ANGLE));
  }

  static double normalizeRelease(double releaseDegrees, double activationDegrees) {
    double activation = normalizeActivation(activationDegrees);
    if (!Double.isFinite(releaseDegrees)) {
      return Math.min(DEFAULT_LOOK_RELEASE, activation - MIN_LOOK_ACTIVATION);
    }
    return Math.max(0D, Math.min(releaseDegrees, activation - MIN_LOOK_ACTIVATION));
  }

  private double getDescentSpeed(int level) {
    return normalizeSpeed(getConfig().descentSpeedBase + (getLevelPercent(level) * getConfig().descentSpeedPerLevel));
  }

  private double getClimbAssist(int level) {
    return normalizeSpeed(getConfig().climbAssistBase + (getLevelPercent(level) * getConfig().climbAssistPerLevel));
  }

  private double lookSensitivity(PlayerRef player) {
    return switch (preference(player, SENSITIVITY)) {
      case NORMAL -> 1D;
      case PRECISE -> 1.5D;
      case RESPONSIVE -> 0.5D;
    };
  }

  private double getLookActivation() {
    return normalizeActivation(getConfig().lookActivationDegrees);
  }

  private double getLookRelease() {
    return normalizeRelease(getConfig().lookReleaseDegrees, getLookActivation());
  }

  @ConfigDescription("Look up to climb quickly, look down to descend quickly, look near the horizon for normal ladder control, and sneak to halt. The first and last two blocks of each climbable column always use normal control.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base downward speed in blocks per tick before per-level scaling.", impact = "Higher values make downward gaze movement faster; runtime motion is capped at one block per tick.")
    double descentSpeedBase = 0.30D;
    @ConfigDoc(value = "Additional downward speed granted at the maximum level.", impact = "Higher values widen the descent-speed gain earned from leveling this adaptation.")
    double descentSpeedPerLevel = 0.30D;
    @ConfigDoc(value = "Base upward speed in blocks per tick before per-level scaling.", impact = "Higher values make upward gaze movement faster.")
    double climbAssistBase = 0.28D;
    @ConfigDoc(value = "Additional upward speed granted at the maximum level.", impact = "Higher values widen the climb-speed gain earned from leveling this adaptation.")
    double climbAssistPerLevel = 0.22D;
    @ConfigDoc(value = "Camera angle above or below the horizon that activates gaze-directed ladder movement.", impact = "Higher values require looking farther up or down before the adaptation takes control.")
    double lookActivationDegrees = DEFAULT_LOOK_ACTIVATION;
    @ConfigDoc(value = "Camera angle where an active gaze direction releases back to normal ladder control.", impact = "This is clamped below the activation angle to prevent rapid direction flicker.")
    double lookReleaseDegrees = DEFAULT_LOOK_RELEASE;
    @ConfigDoc(value = "Prevents fall damage that results directly from a fast ladder descent.", impact = "True negates descent-attributable landing damage; false lets vanilla fall damage apply.")
    boolean safeLanding = true;

    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 1;
    }
  }

  enum Mode {
    NONE,
    CLIMB,
    SLIDE
  }

  private static final class ControlSession {
    private final PlayerRef owner;
    private final UUID worldId;
    private final AtomicBoolean clientClimbingSuppressed = new AtomicBoolean(false);
    private double lastY;
    private long clientTagGeneration = -1L;
    private Id suppressedMaterial;
    private boolean tickScheduled;
    private Mode mode = Mode.NONE;

    private ControlSession(PlayerRef owner, Loc origin) {
      this.owner = owner;
      this.worldId = origin.world().uid();
      this.lastY = origin.y();
    }
  }

  public enum Sensitivity { NORMAL, PRECISE, RESPONSIVE }
}
