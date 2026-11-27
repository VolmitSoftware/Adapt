package art.arcane.adapt.api.insight;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** An active effect on the target. */
@ApiStatus.Experimental
public final class InsightEffect {
  /** The duration of an effect that never runs out. */
  public static final int INFINITE = -1;

  private final Id effect;
  private final int amplifier;
  private final int durationTicks;
  private final boolean harmful;

  private InsightEffect(Id effect, int amplifier, int durationTicks, boolean harmful) {
    this.effect = Objects.requireNonNull(effect, "effect");
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier must not be negative, was " + amplifier);
    }
    if (durationTicks < INFINITE) {
      throw new IllegalArgumentException("durationTicks must be INFINITE or not negative, was " + durationTicks);
    }
    this.amplifier = amplifier;
    this.durationTicks = durationTicks;
    this.harmful = harmful;
  }

  /**
   * An effect that helps its bearer.
   *
   * @param amplifier     the effect level minus one, not negative
   * @param durationTicks the ticks left, or {@link #INFINITE}
   */
  public static InsightEffect beneficial(Id effect, int amplifier, int durationTicks) {
    return new InsightEffect(effect, amplifier, durationTicks, false);
  }

  /** An effect that harms its bearer; the arguments are those of {@link #beneficial}. */
  public static InsightEffect harmful(Id effect, int amplifier, int durationTicks) {
    return new InsightEffect(effect, amplifier, durationTicks, true);
  }

  /** The effect type, such as {@code minecraft:speed}. */
  public Id effectId() {
    return effect;
  }

  /** The effect level minus one. */
  public int amplifier() {
    return amplifier;
  }

  /** The ticks left, or {@link #INFINITE}. */
  public int durationTicks() {
    return durationTicks;
  }

  /** Whether the effect is a harmful one. */
  public boolean harmful() {
    return harmful;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InsightEffect that && effect.equals(that.effect) && amplifier == that.amplifier
        && durationTicks == that.durationTicks && harmful == that.harmful;
  }

  @Override
  public int hashCode() {
    return Objects.hash(effect, amplifier, durationTicks, harmful);
  }

  @Override
  public String toString() {
    return "InsightEffect[" + effect + " " + amplifier + " " + durationTicks + (harmful ? " harmful]" : "]");
  }
}
