package art.arcane.adapt.api.ability;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A short sprint the engine drives each tick: it pushes the player along their movement input, accelerates to a cap
 * and brakes when the input stops or the player can no longer run (flying, swimming, riding, dead). Each adaptation
 * registers one {@link Client} and starts bursts through it; a player holds at most one burst per client.
 *
 * <p>Register while the core runs, for example in the adaptation's constructor, and {@link Client#unregister} it when
 * the adaptation unregisters.
 */
@ApiStatus.Experimental
public final class VelocityBurst {
  private VelocityBurst() {
  }

  /** A client that starts bursts for {@code source} and reports them to {@code feedback}. */
  public static Client register(Adaptation<?> source, Feedback feedback) {
    return Engine.bridge().abilities().velocityBursts(Objects.requireNonNull(source, "source"),
        Objects.requireNonNull(feedback, "feedback"));
  }

  /**
   * How a burst moves the player. Made through {@link #builder()}; every speed starts at 0 and negative or non-finite
   * values count as 0.
   */
  public static final class Profile {
    private final double baseHorizontalSpeed;
    private final double maxHorizontalSpeed;
    private final double accelerationPerTick;
    private final double brakePerTick;
    private final double stopThreshold;
    private final boolean hardStopOnInvalidState;
    private final double fallbackInputVelocityThreshold;

    private Profile(Builder builder) {
      baseHorizontalSpeed = finiteNonNegative(builder.baseHorizontalSpeed);
      maxHorizontalSpeed = finiteNonNegative(builder.maxHorizontalSpeed);
      accelerationPerTick = finiteNonNegative(builder.accelerationPerTick);
      brakePerTick = finiteNonNegative(builder.brakePerTick);
      stopThreshold = finiteNonNegative(builder.stopThreshold);
      hardStopOnInvalidState = builder.hardStopOnInvalidState;
      fallbackInputVelocityThreshold = finiteNonNegative(builder.fallbackInputVelocityThreshold);
    }

    /** A builder with every value at 0 and no hard stop. */
    public static Builder builder() {
      return new Builder();
    }

    private static double finiteNonNegative(double value) {
      return Double.isFinite(value) ? Math.max(0D, value) : 0D;
    }

    /** Horizontal speed at amplifier 0, in blocks per tick. */
    public double baseHorizontalSpeed() {
      return baseHorizontalSpeed;
    }

    /** Speed cap. */
    public double maxHorizontalSpeed() {
      return maxHorizontalSpeed;
    }

    /** Speed gained per tick while input holds. */
    public double accelerationPerTick() {
      return accelerationPerTick;
    }

    /** Speed lost per tick once input stops. */
    public double brakePerTick() {
      return brakePerTick;
    }

    /** Speed below which a braking burst ends. */
    public double stopThreshold() {
      return stopThreshold;
    }

    /** End at once, without braking, when the player can no longer run. */
    public boolean hardStopOnInvalidState() {
      return hardStopOnInvalidState;
    }

    /** Movement speed that counts as input where the client reports no keys. */
    public double fallbackInputVelocityThreshold() {
      return fallbackInputVelocityThreshold;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Profile profile
          && Double.compare(baseHorizontalSpeed, profile.baseHorizontalSpeed) == 0
          && Double.compare(maxHorizontalSpeed, profile.maxHorizontalSpeed) == 0
          && Double.compare(accelerationPerTick, profile.accelerationPerTick) == 0
          && Double.compare(brakePerTick, profile.brakePerTick) == 0
          && Double.compare(stopThreshold, profile.stopThreshold) == 0
          && hardStopOnInvalidState == profile.hardStopOnInvalidState
          && Double.compare(fallbackInputVelocityThreshold, profile.fallbackInputVelocityThreshold) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(baseHorizontalSpeed, maxHorizontalSpeed, accelerationPerTick, brakePerTick, stopThreshold,
          hardStopOnInvalidState, fallbackInputVelocityThreshold);
    }

    @Override
    public String toString() {
      return "Profile[baseHorizontalSpeed=" + baseHorizontalSpeed + ", maxHorizontalSpeed=" + maxHorizontalSpeed
          + ", accelerationPerTick=" + accelerationPerTick + ", brakePerTick=" + brakePerTick + ", stopThreshold="
          + stopThreshold + ", hardStopOnInvalidState=" + hardStopOnInvalidState
          + ", fallbackInputVelocityThreshold=" + fallbackInputVelocityThreshold + "]";
    }

    /** Collects the values of a {@link Profile}. */
    public static final class Builder {
      private double baseHorizontalSpeed;
      private double maxHorizontalSpeed;
      private double accelerationPerTick;
      private double brakePerTick;
      private double stopThreshold;
      private boolean hardStopOnInvalidState;
      private double fallbackInputVelocityThreshold;

      private Builder() {
      }

      /** Horizontal speed at amplifier 0, in blocks per tick. */
      public Builder baseHorizontalSpeed(double value) {
        baseHorizontalSpeed = value;
        return this;
      }

      /** Speed cap. */
      public Builder maxHorizontalSpeed(double value) {
        maxHorizontalSpeed = value;
        return this;
      }

      /** Speed gained per tick while input holds. */
      public Builder accelerationPerTick(double value) {
        accelerationPerTick = value;
        return this;
      }

      /** Speed lost per tick once input stops. */
      public Builder brakePerTick(double value) {
        brakePerTick = value;
        return this;
      }

      /** Speed below which a braking burst ends. */
      public Builder stopThreshold(double value) {
        stopThreshold = value;
        return this;
      }

      /** Whether the burst ends at once, without braking, when the player can no longer run. */
      public Builder hardStopOnInvalidState(boolean value) {
        hardStopOnInvalidState = value;
        return this;
      }

      /** Movement speed that counts as input where the client reports no keys. */
      public Builder fallbackInputVelocityThreshold(double value) {
        fallbackInputVelocityThreshold = value;
        return this;
      }

      /** The profile. */
      public Profile build() {
        return new Profile(this);
      }
    }
  }

  /** One burst to start. Made through {@link #of}; it refuses to overlap a running burst unless {@link #overlapping}. */
  public static final class Request {
    private final int amplifier;
    private final int durationTicks;
    private final boolean overlap;
    private final Profile profile;

    private Request(int amplifier, int durationTicks, boolean overlap, Profile profile) {
      this.amplifier = amplifier;
      this.durationTicks = durationTicks;
      this.overlap = overlap;
      this.profile = Objects.requireNonNull(profile, "profile");
    }

    /** A burst of {@code amplifier} strength lasting {@code durationTicks}, moving the player as {@code profile} says. */
    public static Request of(int amplifier, int durationTicks, Profile profile) {
      return new Request(amplifier, durationTicks, false, profile);
    }

    /** This request, extending a running burst of the same client when {@code extend} is true instead of refusing. */
    public Request overlapping(boolean extend) {
      return new Request(amplifier, durationTicks, extend, profile);
    }

    /** Strength; each step adds to the base speed. */
    public int amplifier() {
      return amplifier;
    }

    /** How long the burst lasts. */
    public int durationTicks() {
      return durationTicks;
    }

    /** Whether a running burst of the same client is extended instead of refused. */
    public boolean overlap() {
      return overlap;
    }

    /** How the burst moves the player. */
    public Profile profile() {
      return profile;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Request request && amplifier == request.amplifier
          && durationTicks == request.durationTicks && overlap == request.overlap && profile.equals(request.profile);
    }

    @Override
    public int hashCode() {
      return Objects.hash(amplifier, durationTicks, overlap, profile);
    }

    @Override
    public String toString() {
      return "Request[amplifier=" + amplifier + ", durationTicks=" + durationTicks + ", overlap=" + overlap
          + ", profile=" + profile + "]";
    }
  }

  /**
   * What {@link Client#start} did.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum StartResult {
    /** A new burst began. */
    STARTED(true),
    /** A running burst was extended. */
    EXTENDED(true),
    /** A burst was running and the request did not allow overlap. */
    REJECTED_ACTIVE(false),
    /** The player already holds the most bursts allowed at once. */
    REJECTED_CAPACITY(false),
    /** The request asked for no time. */
    REJECTED_DURATION(false),
    /** The client was unregistered or the engine is stopping. */
    REJECTED_INACTIVE(false);

    private final boolean accepted;

    StartResult(boolean accepted) {
      this.accepted = accepted;
    }

    /** Whether the burst runs now. */
    public boolean accepted() {
      return accepted;
    }
  }

  /** What the engine reports while a burst runs, on the player's thread. Each method does nothing unless overridden. */
  public interface Feedback {
    /** The burst began. */
    default void onStarted(PlayerRef player) {
    }

    /** One movement update; {@code horizontal} is the push applied and {@code updateIndex} counts from 0. */
    default void onTrail(PlayerRef player, Vec3 horizontal, int updateIndex) {
    }

    /** Input stopped and the burst started braking. */
    default void onBraked(PlayerRef player) {
    }

    /** The burst ended, whatever the reason. */
    default void onEnded(PlayerRef player) {
    }
  }

  /** The bursts one adaptation starts. */
  public interface Client {
    /** Starts or extends the player's burst. */
    StartResult start(PlayerRef player, Request request);

    /** Ends the player's burst, if one runs. */
    void stop(PlayerRef player);

    /** Ends every burst of this client and refuses new ones. */
    void unregister();
  }
}
