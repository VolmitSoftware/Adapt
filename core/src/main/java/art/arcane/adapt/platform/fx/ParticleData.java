package art.arcane.adapt.platform.fx;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;

/** Extra data some particle types need. */
public sealed interface ParticleData {
  ParticleData NONE = new None();

  record None() implements ParticleData {
  }

  record Dust(Rgb color, float size) implements ParticleData {
    public Dust {
      Objects.requireNonNull(color, "color");
    }
  }

  record DustTransition(Rgb from, Rgb to, float size) implements ParticleData {
    public DustTransition {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
    }
  }

  /** Block crack, block marker, falling dust and similar. */
  record Block(BlockStateRef state) implements ParticleData {
    public Block {
      Objects.requireNonNull(state, "state");
    }
  }

  record Item(ItemRef item) implements ParticleData {
    public Item {
      Objects.requireNonNull(item, "item");
    }
  }

  /** Entity effect and similar colour-only particles. */
  record Color(Rgb color) implements ParticleData {
    public Color {
      Objects.requireNonNull(color, "color");
    }
  }

  /** Power or roll of dragon breath and sculk charge particles. */
  record Power(float value) implements ParticleData {
  }
}
