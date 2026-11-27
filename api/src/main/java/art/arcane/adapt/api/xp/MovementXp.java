package art.arcane.adapt.api.xp;

import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

/** The movement rule for movement XP: a real step, neither standing still nor a teleport. */
@ApiStatus.Experimental
public final class MovementXp {
  private MovementXp() {
  }

  /** Whether going from {@code previous} to {@code current} is a move that pays: between 0.25 and 8 blocks in one world. */
  public static boolean moved(Loc previous, Loc current) {
    if (previous == null || current == null || !previous.sameWorld(current)) {
      return false;
    }
    double distance = previous.distanceSquared(current);
    return Double.isFinite(distance) && distance >= 0.0625D && distance <= 64D;
  }
}
