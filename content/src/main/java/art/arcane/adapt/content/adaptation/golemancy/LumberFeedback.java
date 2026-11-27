package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

final class LumberFeedback {
  private static final float VOLUME = 0.8F;
  private static final float PITCH = 1.0F;

  private LumberFeedback() {
  }

  static void play(Adaptation<?> source, Loc at, Kind kind) {
    Id sound = switch (kind) {
      case FELL -> Sounds.BLOCK_WOOD_BREAK;
      case FELL_STEM -> Sounds.BLOCK_STEM_BREAK;
      case SWEEP -> Sounds.BLOCK_GRASS_BREAK;
      case PLANT -> Sounds.BLOCK_GRASS_PLACE;
      case PLANT_FUNGUS -> Sounds.BLOCK_FUNGUS_PLACE;
    };
    Fx.now(source, at, FxPriority.AMBIENT).sound(sound, VOLUME, PITCH);
  }

  enum Kind {
    FELL,
    FELL_STEM,
    SWEEP,
    PLANT,
    PLANT_FUNGUS
  }
}
