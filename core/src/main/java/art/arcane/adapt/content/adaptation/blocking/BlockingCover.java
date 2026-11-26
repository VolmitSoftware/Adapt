package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Finds the players guarding an ally with a raised shield, for the adaptations that protect others. */
public final class BlockingCover {
  private BlockingCover() {
  }

  /**
   * Players within {@code scanRadius} of the ally, other than the ally, who are blocking with a shield, hold the
   * owner adaptation, and whose ally preference accepts the ally. With {@code sneaking}, they must also be sneaking.
   */
  public static List<Guard> guards(Adaptation<?> owner, PlayerRef ally, double scanRadius, boolean sneaking) {
    Loc allyLocation = ally.location();
    List<Guard> guards = new ArrayList<>();
    for (EntityRef nearby : allyLocation.world().nearbyEntities(allyLocation.position(), scanRadius, scanRadius, scanRadius)) {
      if (!(nearby instanceof PlayerRef blocker) || blocker.uuid().equals(ally.uuid())
          || (sneaking && !blocker.isSneaking()) || !blocker.isBlocking() || !BlockingShields.held(owner, blocker)) {
        continue;
      }

      int level = owner.getActiveLevel(blocker);
      if (level <= 0 || !owner.preference(blocker, BlockingPreferences.ALLIES).accepts(blocker, ally)) {
        continue;
      }
      guards.add(new Guard(blocker, level));
    }
    return guards;
  }

  /** True when the unit vector from guard to ally lies within the cone of {@code arcCos} around {@code direction}. */
  public static boolean shelters(Vec3 direction, Vec3 guardToAlly, double arcCos) {
    return direction.dot(guardToAlly) >= arcCos;
  }

  /** Cosine of half the arc, for {@link #shelters}. */
  public static double arcCos(double arcDegrees) {
    return Math.cos(Math.toRadians(arcDegrees * 0.5D));
  }

  public record Guard(PlayerRef blocker, int level) {
  }
}
