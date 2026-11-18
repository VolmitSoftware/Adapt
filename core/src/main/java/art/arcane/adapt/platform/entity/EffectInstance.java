package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** Potion effect value. Duration is in ticks; {@link #INFINITE} means no expiry. */
public record EffectInstance(Id type, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
  public static final int INFINITE = -1;

  public EffectInstance {
    Objects.requireNonNull(type, "type");
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier must be >= 0: " + amplifier);
    }
  }

  public static EffectInstance of(Id type, int durationTicks, int amplifier) {
    return new EffectInstance(type, durationTicks, amplifier, false, true, true);
  }

  public boolean isInfinite() {
    return durationTicks == INFINITE;
  }
}
