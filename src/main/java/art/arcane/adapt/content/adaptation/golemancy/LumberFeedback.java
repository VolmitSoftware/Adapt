package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import org.bukkit.Location;
import org.bukkit.Sound;

final class LumberFeedback {
  private static final float VOLUME = 0.8F;
  private static final float PITCH = 1.0F;

  private LumberFeedback() {
  }

  static void play(Adaptation<?> source, Location at, Kind kind) {
    Sound sound = switch (kind) {
      case FELL -> Sound.BLOCK_WOOD_BREAK;
      case FELL_STEM -> Sound.BLOCK_STEM_BREAK;
      case SWEEP -> Sound.BLOCK_GRASS_BREAK;
      case PLANT -> Sound.BLOCK_GRASS_PLACE;
      case PLANT_FUNGUS -> Sound.BLOCK_FUNGUS_PLACE;
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
