package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

final class AnglerFeedback {
  private static final int CAST_PARTICLES = 4;
  private static final int CATCH_PARTICLES = 12;
  private static final double SPREAD = 0.3D;
  private static final double SPEED = 0.05D;
  private static final float VOLUME = 0.6F;
  private static final float PITCH = 1.0F;

  private AnglerFeedback() {
  }

  static void play(Adaptation<?> source, Location at, Kind kind) {
    Sound sound = switch (kind) {
      case CAST -> Sound.ENTITY_FISHING_BOBBER_THROW;
      case CATCH -> Sound.ENTITY_FISHING_BOBBER_SPLASH;
    };
    int particles = switch (kind) {
      case CAST -> CAST_PARTICLES;
      case CATCH -> CATCH_PARTICLES;
    };
    Fx.now(source, at, FxPriority.AMBIENT)
        .sound(sound, VOLUME, PITCH)
        .particle(Particle.SPLASH, particles, 0D, 0D, 0D, SPREAD, SPEED);
  }

  enum Kind {
    CAST,
    CATCH
  }
}
