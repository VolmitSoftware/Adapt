package art.arcane.adapt.platform.fx;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Particle type plus data. Build once and keep in a constant; adapters may cache the native form
 * per spec instance, so reusing specs keeps emission allocation-free.
 *
 * @param type particle type id, for example {@code minecraft:flame}
 * @param data extra data the type needs, or {@link ParticleData#NONE}
 */
@ApiStatus.AvailableSince("1.0.0")
public record ParticleSpec(Id type, ParticleData data) {
  public ParticleSpec {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(data, "data");
  }

  /** Spec for a particle type that takes no data. */
  public static ParticleSpec of(Id type) {
    return new ParticleSpec(type, ParticleData.NONE);
  }

  /** {@code minecraft:dust} with the colour and scale. */
  public static ParticleSpec dust(Rgb color, float size) {
    return new ParticleSpec(Id.minecraft("dust"), new ParticleData.Dust(color, size));
  }

  /** {@code minecraft:block} crack particles of the state. */
  public static ParticleSpec block(BlockStateRef state) {
    return new ParticleSpec(Id.minecraft("block"), new ParticleData.Block(state));
  }
}
