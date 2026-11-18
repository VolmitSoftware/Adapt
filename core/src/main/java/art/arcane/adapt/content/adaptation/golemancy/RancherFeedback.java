package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

final class RancherFeedback {
  private static final int HEARTS = 5;
  private static final double HEART_SPREAD = 0.4D;
  private static final float VOLUME = 0.8F;
  private static final float PITCH = 1.0F;

  private RancherFeedback() {
  }

  static void play(Adaptation<?> source, Loc at, Kind kind) {
    Id sound = switch (kind) {
      case BREED -> Sounds.ENTITY_GENERIC_EAT;
      case SHEAR -> Sounds.ENTITY_SHEEP_SHEAR;
      case MILK -> Sounds.ENTITY_COW_MILK;
      case GOAT_MILK -> Sounds.ENTITY_GOAT_MILK;
      case STEW -> Sounds.ENTITY_MOOSHROOM_MILK;
      case CULL -> Sounds.ENTITY_IRON_GOLEM_ATTACK;
    };
    FxEmitter fx = Fx.now(source, at, FxPriority.AMBIENT);
    if (kind == Kind.BREED) {
      fx.burst(Particles.HEART, HEARTS, HEART_SPREAD);
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
