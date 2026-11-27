package art.arcane.adapt.platform.fx;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Extra data some particle types need. */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface ParticleData {
  /** Shared instance for particles that take no data. */
  ParticleData NONE = new None();

  /** No extra data. */
  record None() implements ParticleData {
  }

  /**
   * Redstone dust particle data.
   *
   * @param color dust colour
   * @param size particle scale, 1 for vanilla redstone dust
   */
  record Dust(Rgb color, float size) implements ParticleData {
    public Dust {
      Objects.requireNonNull(color, "color");
    }
  }

  /**
   * Dust that fades from one colour to another.
   *
   * @param from starting colour
   * @param to final colour
   * @param size particle scale
   */
  record DustTransition(Rgb from, Rgb to, float size) implements ParticleData {
    public DustTransition {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
    }
  }

  /**
   * Block crack, block marker, falling dust and similar.
   *
   * @param state block state the particle shows
   */
  record Block(BlockStateRef state) implements ParticleData {
    public Block {
      Objects.requireNonNull(state, "state");
    }
  }

  /**
   * Item crack particle data.
   *
   * @param item item the particle shows
   */
  record Item(ItemRef item) implements ParticleData {
    public Item {
      Objects.requireNonNull(item, "item");
    }
  }

  /**
   * Entity effect and similar colour-only particles.
   *
   * @param color particle colour
   */
  record Color(Rgb color) implements ParticleData {
    public Color {
      Objects.requireNonNull(color, "color");
    }
  }

  /**
   * Power or roll of dragon breath and sculk charge particles.
   *
   * @param value the power or roll value
   */
  record Power(float value) implements ParticleData {
  }
}
