package art.arcane.adapt.platform.fx;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;

/**
 * Particle type plus data. Build once and keep in a constant; adapters may cache the native form
 * per spec instance, so reusing specs keeps emission allocation-free.
 */
public record ParticleSpec(Id type, ParticleData data) {
  public ParticleSpec {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(data, "data");
  }

  public static ParticleSpec of(Id type) {
    return new ParticleSpec(type, ParticleData.NONE);
  }

  public static ParticleSpec dust(Rgb color, float size) {
    return new ParticleSpec(Id.minecraft("dust"), new ParticleData.Dust(color, size));
  }

  public static ParticleSpec block(BlockStateRef state) {
    return new ParticleSpec(Id.minecraft("block"), new ParticleData.Block(state));
  }
}
