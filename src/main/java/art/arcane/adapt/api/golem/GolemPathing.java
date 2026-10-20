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
  private static final Map<Entity, Location> DESTINATIONS = Collections.synchronizedMap(new WeakHashMap<>());

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

  static boolean leadsTo(Entity entity, Location target) {
    Location destination = DESTINATIONS.get(entity);
    return destination != null
        && Objects.equals(destination.getWorld(), target.getWorld())
        && destination.getBlockX() == target.getBlockX()
        && destination.getBlockY() == target.getBlockY()
        && destination.getBlockZ() == target.getBlockZ();
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
    DESTINATIONS.put(mob, destination);
  }

  void stop() {
    if (destination == null) {
      return;
    }
    destination = null;
    retarget = false;
    issued = false;
    DESTINATIONS.remove(mob);
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
    PaperCompat.pathfindTo(mob, destination, speed);
  }
}
