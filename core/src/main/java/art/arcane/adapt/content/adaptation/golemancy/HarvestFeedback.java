package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

final class HarvestFeedback {
  private static final int BONE_MEAL_PARTICLES = 15;
  private static final float VOLUME = 0.8F;
  private static final float PITCH = 1.0F;

  private HarvestFeedback() {
  }

  static void play(Adaptation<?> source, Loc at, Kind kind) {
    Id sound = switch (kind) {
      case HARVEST -> Sounds.BLOCK_CROP_BREAK;
      case PICK -> Sounds.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES;
      case PLANT -> Sounds.ITEM_CROP_PLANT;
      case TILL -> Sounds.ITEM_HOE_TILL;
      case TEND -> Sounds.ITEM_BONE_MEAL_USE;
    };
    if (kind == Kind.TEND) {
      AdaptCore.platform().fx().boneMealParticles(at.world(), at.blockPos(), BONE_MEAL_PARTICLES);
    }
    Fx.now(source, at, FxPriority.AMBIENT).sound(sound, VOLUME, PITCH);
  }

  enum Kind {
    HARVEST,
    PICK,
    PLANT,
    TILL,
    TEND
  }
}
