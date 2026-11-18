package art.arcane.adapt.api.xp;

import art.arcane.adapt.platform.value.Loc;

public final class MovementXp {
  private MovementXp() {
  }

  public static boolean moved(Loc previous, Loc current) {
    if (previous == null || current == null || !previous.sameWorld(current)) {
      return false;
    }
    double distance = previous.distanceSquared(current);
    return Double.isFinite(distance) && distance >= 0.0625D && distance <= 64D;
  }
}
