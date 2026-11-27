package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Potion effect value. Duration is in ticks; {@link #INFINITE} means no expiry. Start from {@link #of} and change the
 * display flags with the {@code with} methods; each returns a new value.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class EffectInstance {
  /** Duration value for an effect that never expires. */
  public static final int INFINITE = -1;

  private final Id type;
  private final int durationTicks;
  private final int amplifier;
  private final boolean ambient;
  private final boolean particles;
  private final boolean icon;

  private EffectInstance(Id type, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
    this.type = Objects.requireNonNull(type, "type");
    if (amplifier < 0) {
      throw new IllegalArgumentException("amplifier must be >= 0: " + amplifier);
    }
    this.durationTicks = durationTicks;
    this.amplifier = amplifier;
    this.ambient = ambient;
    this.particles = particles;
    this.icon = icon;
  }

  /** Effect with particles and icon shown and no ambient flag. */
  public static EffectInstance of(Id type, int durationTicks, int amplifier) {
    return new EffectInstance(type, durationTicks, amplifier, false, true, true);
  }

  /** This effect with or without the ambient flag. */
  public EffectInstance withAmbient(boolean newAmbient) {
    return new EffectInstance(type, durationTicks, amplifier, newAmbient, particles, icon);
  }

  /** This effect with or without particles. */
  public EffectInstance withParticles(boolean newParticles) {
    return new EffectInstance(type, durationTicks, amplifier, ambient, newParticles, icon);
  }

  /** This effect with or without the icon on the player's screen. */
  public EffectInstance withIcon(boolean newIcon) {
    return new EffectInstance(type, durationTicks, amplifier, ambient, particles, newIcon);
  }

  /** Effect type id, for example {@code minecraft:speed}. */
  public Id type() {
    return type;
  }

  /** Duration in ticks, or {@link #INFINITE}. */
  public int durationTicks() {
    return durationTicks;
  }

  /** Level minus one: 0 is level I; never negative. */
  public int amplifier() {
    return amplifier;
  }

  /** Ambient effect, as from a beacon, with fainter particles. */
  public boolean ambient() {
    return ambient;
  }

  /** Shows particles. */
  public boolean particles() {
    return particles;
  }

  /** Shows the effect icon on the player's screen. */
  public boolean icon() {
    return icon;
  }

  /** True when the duration is {@link #INFINITE}. */
  public boolean isInfinite() {
    return durationTicks == INFINITE;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EffectInstance effect && type.equals(effect.type) && durationTicks == effect.durationTicks
        && amplifier == effect.amplifier && ambient == effect.ambient && particles == effect.particles
        && icon == effect.icon;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, durationTicks, amplifier, ambient, particles, icon);
  }

  @Override
  public String toString() {
    return "EffectInstance[type=" + type + ", durationTicks=" + durationTicks + ", amplifier=" + amplifier
        + ", ambient=" + ambient + ", particles=" + particles + ", icon=" + icon + "]";
  }
}
