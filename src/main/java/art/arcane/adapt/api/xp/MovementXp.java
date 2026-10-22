package art.arcane.adapt.api.xp;

import org.bukkit.Location;

public final class MovementXp {
  private MovementXp() {
  }

  public static boolean moved(Location previous, Location current) {
    if (previous == null || current == null || previous.getWorld() != current.getWorld()) {
      return false;
    }
    double distance = previous.distanceSquared(current);
    return Double.isFinite(distance) && distance >= 0.0625D && distance <= 64D;
  }
}
