package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

final class RancherFeedback {
  private static final int HEARTS = 5;
  private static final double HEART_SPREAD = 0.4D;
  private static final float VOLUME = 0.8F;
  private static final float PITCH = 1.0F;

  private RancherFeedback() {
  }

  static void play(Adaptation<?> source, Location at, Kind kind) {
    Sound sound = switch (kind) {
      case BREED -> Sound.ENTITY_GENERIC_EAT;
      case SHEAR -> Sound.ENTITY_SHEEP_SHEAR;
      case MILK -> Sound.ENTITY_COW_MILK;
      case GOAT_MILK -> Sound.ENTITY_GOAT_MILK;
      case STEW -> Sound.ENTITY_MOOSHROOM_MILK;
      case CULL -> Sound.ENTITY_IRON_GOLEM_ATTACK;
    };
    FxEmitter fx = Fx.now(source, at, FxPriority.AMBIENT);
    if (kind == Kind.BREED) {
      fx.burst(Particle.HEART, HEARTS, HEART_SPREAD);
    }
    fx.sound(sound, VOLUME, PITCH);
  }

  enum Kind {
    BREED,
    SHEAR,
    MILK,
    GOAT_MILK,
    STEW,
    CULL
  }
}
