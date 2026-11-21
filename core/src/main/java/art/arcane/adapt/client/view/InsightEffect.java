package art.arcane.adapt.client.view;

import java.util.Objects;

/** An active effect on the target; {@code durationTicks} is -1 for an infinite effect. */
public record InsightEffect(String effectId, int amplifier, int durationTicks, boolean harmful) {
  public static final int INFINITE = -1;

  public InsightEffect {
    Objects.requireNonNull(effectId, "effectId");
  }
}
