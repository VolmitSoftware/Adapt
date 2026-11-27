package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The prone slide: a fixed swimming pose with reduced ground friction, held each tick until it runs out or is
 * interrupted, then the captured pose is restored. The owner's {@link AgilitySlipstreamSlide#CONTROL} preference
 * decides whether releasing sneak ends it.
 */
public final class AgilitySlide {
  private static final int FRICTION_CLEANUP_GRACE_TICKS = 2;
  private static final String FRICTION_SLOT = "slide-friction";

  private final Adaptation<?> owner;
  private final Map<UUID, ForcedPoseState> forcedPoses = new ConcurrentHashMap<>();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean(false);

  public AgilitySlide(Adaptation<?> owner) {
    this.owner = owner;
  }

  /** Sprinting or just sprinted, on the ground, on foot and in survival or adventure. */
  public static boolean eligible(PlayerRef p, boolean recentlySprinted) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    boolean sprinting = p.isSprinting() || recentlySprinted;
    return sprinting
        && p.isOnGround()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && p.vehicle() == null;
  }

  public boolean accepting() {
    return !lifecycleCleanupStarted.get();
  }

  public boolean sliding(UUID id) {
    return forcedPoses.containsKey(id);
  }

  /** Throws the player along {@code direction} and holds the prone pose for {@code proneTicks}. */
  public Vec3 launch(PlayerRef p, Vec3 direction, double force, int proneTicks, double frictionReduction) {
    Vec3 velocity = direction.multiply(force).withY(Math.min(p.velocity().y(), -0.04D));
    p.setVelocity(velocity);
    start(p, proneTicks, frictionReduction, Attributes.FRICTION_MODIFIER);

    AgilitySlipstreamSlide.slideFx(owner, p.location(), velocity);
    return velocity;
  }

  public void start(PlayerRef p, int proneTicks, double frictionReduction, Id frictionAttribute) {
    if (lifecycleCleanupStarted.get()) {
      return;
    }

    stop(p);
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
        AdaptAttributeService.get().applyTimed(p, owner.getId().toString(), FRICTION_SLOT, frictionAttribute,
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

  public void stop(PlayerRef p) {
    ForcedPoseState state = forcedPoses.remove(p.uuid());
    if (state != null) {
      restorePose(state);
    }
  }

  /** Stops accepting slides and restores every held pose; called once when the owner unregisters. */
  public boolean shutdown() {
    if (!lifecycleCleanupStarted.compareAndSet(false, true)) {
      return false;
    }
    ForcedPoseState[] states = forcedPoses.values().toArray(new ForcedPoseState[0]);
    for (ForcedPoseState state : states) {
      if (forcedPoses.remove(state.owner.uuid(), state)) {
        dispatchPoseRestore(state);
      }
    }
    forcedPoses.clear();
    return true;
  }

  /** Slows living non-players the sliding player passes through, each tick for {@code ticksLeft} ticks. */
  public void slowThrough(PlayerRef p, int ticksLeft, int slowDurationTicks, double slowAmount) {
    if (ticksLeft <= 0 || slowDurationTicks <= 0 || !p.isOnline() || p.isDead()
        || !forcedPoses.containsKey(p.uuid()) || !owner.isPlayerEnabled(p)) {
      return;
    }

    for (EntityRef nearby : p.nearbyEntities(1.3D, 1.0D, 1.3D)) {
      if (nearby instanceof LivingRef target && !(nearby instanceof PlayerRef) && owner.canDamageTarget(p, nearby)) {
        AdaptAttributeService.get().applyTimed(target, owner.getId().toString(), "slow", Attributes.MOVEMENT_SPEED,
            slowAmount, AttributeOperation.ADD_MULTIPLIED_TOTAL, slowDurationTicks);
      }
    }

    AdaptApi.scheduler().runEntity(p, () -> slowThrough(p, ticksLeft - 1, slowDurationTicks, slowAmount), 1);
  }

  private void maintainProne(ForcedPoseState state) {
    PlayerRef p = state.owner;
    UUID id = p.uuid();
    if (forcedPoses.get(id) != state) {
      return;
    }

    if (!p.isOnline() || p.isDead() || !owner.hasActiveAdaptation(p) || isSlideInterrupted(p)
        || (owner.preference(p, AgilitySlipstreamSlide.CONTROL) == AgilitySlipstreamSlide.Control.HOLD && !p.isSneaking())) {
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
    if (lifecycleCleanupStarted.get() || !AdaptApi.scheduler().runEntity(state.owner, () -> runProneMaintenance(state), 1)) {
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

  private void dispatchPoseRestore(ForcedPoseState state) {
    PlayerRef p = state.owner;
    Runnable restore = () -> restorePose(state);
    if (!AdaptApi.scheduler().runEntity(p, restore) && canRestoreDirectly(p)) {
      restore.run();
    }
  }

  private void restorePose(ForcedPoseState state) {
    PlayerRef p = state.owner;
    if (state.frictionApplied) {
      AdaptAttributeService.get().remove(p, owner.getId().toString(), FRICTION_SLOT, state.frictionAttribute);
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

  private static boolean canRestoreDirectly(PlayerRef p) {
    return AdaptApi.scheduler().isRegionized() ? AdaptApi.scheduler().owns(p) : AdaptApi.scheduler().isTickThread();
  }

  private static boolean isSlideInterrupted(PlayerRef p) {
    GameMode mode = p.gameMode();
    return (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE)
        || p.isSwimming()
        || p.isFlying()
        || p.isGliding()
        || p.vehicle() != null;
  }

  static boolean isProneActive(long untilMillis, long nowMillis) {
    return untilMillis > nowMillis;
  }

  static double slideFrictionReduction(double configuredReduction) {
    if (!Double.isFinite(configuredReduction)) {
      return 0D;
    }
    return Math.max(0D, Math.min(1D, configuredReduction));
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
}
