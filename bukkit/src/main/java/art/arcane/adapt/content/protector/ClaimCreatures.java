package art.arcane.adapt.content.protector;

import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Panda;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.WaterMob;

import java.util.function.Predicate;

final class ClaimCreatures {
  private ClaimCreatures() {
  }

  static boolean protectedCreature(Entity entity, Predicate<Entity> lured) {
    return (entity instanceof Creature || entity instanceof WaterMob) && !monster(entity, lured);
  }

  private static boolean monster(Entity entity, Predicate<Entity> lured) {
    if (entity instanceof Monster || entity instanceof Shulker) {
      return true;
    }
    if (entity instanceof Rabbit rabbit) {
      return rabbit.getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY;
    }
    if (entity instanceof Panda panda) {
      return panda.getMainGene() == Panda.Gene.AGGRESSIVE;
    }
    if ((entity instanceof Hoglin || entity instanceof PolarBear) && entity instanceof Mob mob) {
      return !lured.test(entity) && mob.getTarget() != null;
    }
    return false;
  }
}
