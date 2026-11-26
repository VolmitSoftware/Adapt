package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

/** Tidecaller's velocity surge: direction, wall check and launch velocity. */
public final class SeaborneSurge {
  private SeaborneSurge() {
  }

  public static boolean inWater(PlayerRef player) {
    WorldRef world = player.world();
    return player.isInWater() || player.isSwimming() || world.blockAt(player.location()).isLiquid()
        || world.blockAt(player.eyeLocation()).isLiquid();
  }

  public static Vec3 direction(PlayerRef player, boolean flatten) {
    Vec3 direction = player.location().direction();
    if (flatten) {
      direction = direction.withY(0);
    }
    if (direction.lengthSquared() <= 0.000001) {
      return Vec3.ZERO;
    }
    return direction.normalize();
  }

  public static boolean blockedAhead(PlayerRef player, Vec3 direction, double distance) {
    return player.world().rayTraceBlock(player.eyeLocation().position(), direction, Math.max(0.1, distance), false) != null;
  }

  public static Vec3 velocity(Vec3 current, Vec3 direction, double strength, double vertical, boolean additive, double maxVelocity) {
    Vec3 velocity = direction.normalize().multiply(Math.max(0, strength));
    if (additive) {
      velocity = current.add(velocity).add(new Vec3(0, vertical, 0));
    } else {
      velocity = velocity.withY(vertical);
    }
    double max = Math.max(0, maxVelocity);
    if (max > 0 && velocity.lengthSquared() > max * max) {
      velocity = velocity.normalize().multiply(max);
    }
    return velocity;
  }

  public static void keepSwimming(PlayerRef player) {
    if (!inWater(player)) {
      return;
    }
    player.setSwimming(true);
    Tasks.runEntity(player, () -> {
      if (player.isOnline() && inWater(player)) {
        player.setSwimming(true);
      }
    }, 1);
  }
}
