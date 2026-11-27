package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ObjDoubleConsumer;

/**
 * Movement speed that ramps up the longer a player keeps running. The owner decides when the run continues; each
 * update adds the elapsed ticks, smooths the speed bonus toward the ramp's target and plays the charge feedback.
 */
public final class AgilityRunRamp {
  private static final String SLOT_WINDUP = "windup";

  private final Adaptation<?> owner;
  private final Map<UUID, State> states;
  private final ObjDoubleConsumer<PlayerRef> atFullSpeed;

  /** {@code atFullSpeed} receives the ticks spent at or past full progress while moving. */
  public AgilityRunRamp(Adaptation<?> owner, Map<UUID, State> states, ObjDoubleConsumer<PlayerRef> atFullSpeed) {
    this.owner = owner;
    this.states = states;
    this.atFullSpeed = atFullSpeed;
  }

  /**
   * How a run turns into speed. {@code bonus} maps progress to the relative speed bonus; progress is the run length
   * over {@code ticksToMax}, held at 1 when {@code capped}.
   */
  public record Ramp(double ticksToMax, boolean capped, DoubleUnaryOperator bonus, double smoothingPerTick,
                     double velocityThreshold) {
  }

  public boolean tracking(UUID id) {
    return !states.isEmpty() && states.containsKey(id);
  }

  public double bonus(UUID id) {
    State state = states.get(id);
    return state == null ? 0D : state.currentBonus;
  }

  /** Adds the ticks since the last update at {@code now} to the run and moves the speed bonus toward the ramp. */
  public void update(PlayerRef p, Ramp ramp, long now) {
    UUID id = p.uuid();
    State state = states.computeIfAbsent(id, (UUID ignored) -> new State());
    double elapsedTicks = elapsedTicks(state.lastUpdateAt, now);
    state.lastUpdateAt = now;
    if (elapsedTicks <= 0D) {
      return;
    }
    state.runningTicks += elapsedTicks;

    double ticksToMax = Math.max(1D, ramp.ticksToMax());
    double progress = ramp.capped()
        ? Math.min(((state.runningTicks - 0) / (ticksToMax - 0)), 1)
        : state.runningTicks / ticksToMax;
    applyBoost(p, state, ramp.bonus().applyAsDouble(progress), ramp.smoothingPerTick(), elapsedTicks);
    emitChargeFeedback(p, state, progress);

    if (progress >= 1.0 && isMovingHorizontally(p, ramp.velocityThreshold())) {
      atFullSpeed.accept(p, elapsedTicks);
      if (ThreadLocalRandom.current().nextDouble() < 0.25) {
        AgilityWindUp.fullSpeedTrailFx(owner, p);
      }
    }
  }

  public void clear(PlayerRef p) {
    if (p == null) {
      return;
    }

    State state = states.remove(p.uuid());
    if (state != null) {
      clearBoost(p, state);
    }
  }

  /** Survival or adventure, alive, on foot and not swimming, flying or gliding; sneaking only when allowed. */
  public static boolean eligible(PlayerRef p, boolean allowSneak) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isDead()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && (allowSneak || !p.isSneaking())
        && p.vehicle() == null;
  }

  private void emitChargeFeedback(PlayerRef p, State state, double progress) {
    int bracket = (int) Math.floor(progress / 0.25D);
    if (bracket <= state.lastBracket) {
      return;
    }

    state.lastBracket = bracket;
    if (progress >= 1.0D) {
      if (state.ignited) {
        return;
      }

      state.ignited = true;
      AgilityWindUp.igniteFx(owner, p);
      return;
    }

    if (bracket < 1) {
      return;
    }

    AgilityWindUp.chargeFx(owner, p, bracket);
  }

  private void applyBoost(PlayerRef p, State state, double target, double smoothingPerTick, double elapsedTicks) {
    if (!state.boosting) {
      state.boosting = true;
      state.currentBonus = 0D;
    }

    float smoothing = elapsedSmoothing(smoothingPerTick, elapsedTicks);
    double next = smoothedBonus(state.currentBonus, target, smoothing);
    if (Math.abs(state.currentBonus - next) > 0.0005D) {
      state.currentBonus = next;
      AdaptAttributeService.get().apply(p, owner.getId().toString(), SLOT_WINDUP, Attributes.MOVEMENT_SPEED, next,
          AttributeOperation.ADD_MULTIPLIED_TOTAL);
    }
  }

  private void clearBoost(PlayerRef p, State state) {
    if (!state.boosting) {
      return;
    }

    boolean wasCharged = state.lastBracket >= 1;
    state.boosting = false;
    state.lastBracket = 0;
    state.ignited = false;
    state.currentBonus = 0D;
    AdaptAttributeService.get().remove(p, owner.getId().toString(), SLOT_WINDUP, Attributes.MOVEMENT_SPEED);

    if (wasCharged) {
      AgilityWindUp.winddownFx(owner, p);
    }
  }

  private static boolean isMovingHorizontally(PlayerRef p, double velocityThreshold) {
    Vec3 velocity = p.velocity();
    double threshold = Math.max(0D, velocityThreshold);
    return (velocity.x() * velocity.x()) + (velocity.z() * velocity.z()) > (threshold * threshold);
  }

  static double smoothedBonus(double currentBonus, double target, float smoothing) {
    double next = currentBonus + ((target - currentBonus) * smoothing);
    if (Math.abs(target - next) < 0.0025D) {
      return target;
    }
    return next;
  }

  static double elapsedTicks(long previousUpdateAt, long now) {
    if (previousUpdateAt <= 0L) {
      return 1D;
    }
    if (now <= previousUpdateAt) {
      return 0D;
    }
    return Math.min(20D, (now - previousUpdateAt) / 50D);
  }

  static float elapsedSmoothing(double perTickSmoothing, double elapsedTicks) {
    double clamped = Math.max(0D, Math.min(1D, perTickSmoothing));
    if (clamped <= 0D || elapsedTicks <= 0D) {
      return 0F;
    }
    return (float) (1D - Math.pow(1D - clamped, elapsedTicks));
  }

  public static final class State {
    private long lastUpdateAt;
    private double runningTicks;
    private boolean boosting;
    private double currentBonus;
    private int lastBracket;
    private boolean ignited;
  }
}
