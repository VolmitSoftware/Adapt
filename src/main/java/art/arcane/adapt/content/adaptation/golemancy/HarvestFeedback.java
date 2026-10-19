package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

final class HarvestFeedback {
  private static final int BONE_MEAL_PARTICLES = 15;
  private static final float VOLUME = 0.8F;
  private static final float PITCH = 1.0F;

  private HarvestFeedback() {
  }

  static void play(Adaptation<?> source, Location at, Kind kind) {
    Sound sound = switch (kind) {
      case HARVEST -> Sound.BLOCK_CROP_BREAK;
      case PICK -> Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES;
      case PLANT -> Sound.ITEM_CROP_PLANT;
      case TILL -> Sound.ITEM_HOE_TILL;
      case TEND -> Sound.ITEM_BONE_MEAL_USE;
    };
    World world = at.getWorld();
    if (kind == Kind.TEND && world != null) {
      world.playEffect(at, Effect.BONE_MEAL_USE, BONE_MEAL_PARTICLES);
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
