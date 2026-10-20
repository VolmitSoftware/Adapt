package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;

final class GolemPathing {
  private static final double RETARGET_DISTANCE_SQUARED = 1D;
  private static final Map<Entity, Location> PATH_TARGETS = Collections.synchronizedMap(new WeakHashMap<>());
  private static final ThreadLocal<Entity> ISSUING = new ThreadLocal<>();

  private final Mob mob;
  private final IntSupplier intervalTicks;
  private Location destination;
  private double speed;
  private boolean retarget;
  private final boolean reissueWhilePathing;
  private boolean issued;
  private int issuedAt;

  GolemPathing(Mob mob, IntSupplier intervalTicks, boolean reissueWhilePathing) {
    this.mob = Objects.requireNonNull(mob);
    this.intervalTicks = Objects.requireNonNull(intervalTicks);
    this.reissueWhilePathing = reissueWhilePathing;
  }

  static boolean ownsPath(Entity entity, Location target) {
    if (entity.equals(ISSUING.get())) {
      PATH_TARGETS.put(entity, target.clone());
      return true;
    }
    Location pathTarget = PATH_TARGETS.get(entity);
    return pathTarget != null
        && Objects.equals(pathTarget.getWorld(), target.getWorld())
        && pathTarget.getBlockX() == target.getBlockX()
        && pathTarget.getBlockY() == target.getBlockY()
        && pathTarget.getBlockZ() == target.getBlockZ();
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
    PATH_TARGETS.remove(mob);
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
    } else if (issued && !reissueWhilePathing && PaperCompat.hasPath(mob)) {
      issuedAt = now;
      return;
    }
    issued = true;
    issuedAt = now;
    ISSUING.set(mob);
    try {
      PaperCompat.pathfindTo(mob, destination, speed);
    } finally {
      ISSUING.remove();
    }
  }
}
