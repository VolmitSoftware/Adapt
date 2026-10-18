package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

import java.util.Objects;
import java.util.function.IntSupplier;

final class GolemPathing {
  private static final double RETARGET_DISTANCE_SQUARED = 1D;

  private final Mob mob;
  private final IntSupplier intervalTicks;
  private Location destination;
  private double speed;
  private boolean retarget;
  private boolean issued;
  private int issuedAt;

  GolemPathing(Mob mob, IntSupplier intervalTicks) {
    this.mob = Objects.requireNonNull(mob);
    this.intervalTicks = Objects.requireNonNull(intervalTicks);
  }

  boolean hasDestination() {
    return destination != null;
  }

  void moveTo(Location next, double nextSpeed) {
    speed = nextSpeed;
    if (destination != null
        && Objects.equals(destination.getWorld(), next.getWorld())
        && destination.distanceSquared(next) <= RETARGET_DISTANCE_SQUARED) {
      return;
    }
    destination = next.clone();
    retarget = true;
  }

  void stop() {
    if (destination == null) {
      return;
    }
    destination = null;
    retarget = false;
    issued = false;
    PaperCompat.stopPathfinding(mob);
  }

  void restart() {
    if (destination == null) {
      return;
    }
    retarget = true;
    issued = false;
  }

  void tick() {
    if (destination == null) {
      return;
    }
    int now = mob.getTicksLived();
    if (issued && now - issuedAt < Math.max(1, intervalTicks.getAsInt())) {
      return;
    }
    if (retarget) {
      retarget = false;
      PaperCompat.stopPathfinding(mob);
    }
    issued = true;
    issuedAt = now;
    PaperCompat.pathfindTo(mob, destination, speed);
  }
}
