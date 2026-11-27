package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

final class GolemPathing {
  private static final double RETARGET_DISTANCE_SQUARED = 1D;
  private static final Map<UUID, Loc> PATH_TARGETS = new ConcurrentHashMap<>();
  private static final ThreadLocal<UUID> ISSUING = new ThreadLocal<>();

  private final MobRef mob;
  private final IntSupplier intervalTicks;
  private Loc destination;
  private double speed;
  private boolean retarget;
  private final boolean reissueWhilePathing;
  private boolean issued;
  private int issuedAt;

  GolemPathing(MobRef mob, IntSupplier intervalTicks, boolean reissueWhilePathing) {
    this.mob = Objects.requireNonNull(mob);
    this.intervalTicks = Objects.requireNonNull(intervalTicks);
    this.reissueWhilePathing = reissueWhilePathing;
  }

  static boolean ownsPath(EntityRef entity, Loc target) {
    if (entity.uuid().equals(ISSUING.get())) {
      PATH_TARGETS.put(entity.uuid(), target);
      return true;
    }
    Loc pathTarget = PATH_TARGETS.get(entity.uuid());
    return pathTarget != null && target != null
        && pathTarget.sameWorld(target)
        && pathTarget.blockX() == target.blockX()
        && pathTarget.blockY() == target.blockY()
        && pathTarget.blockZ() == target.blockZ();
  }

  boolean hasDestination() {
    return destination != null;
  }

  void moveTo(Loc next, double nextSpeed) {
    speed = nextSpeed;
    if (destination != null
        && destination.sameWorld(next)
        && destination.distanceSquared(next) <= RETARGET_DISTANCE_SQUARED) {
      return;
    }
    destination = next;
    retarget = true;
  }

  void stop() {
    if (destination == null) {
      return;
    }
    destination = null;
    retarget = false;
    issued = false;
    PATH_TARGETS.remove(mob.uuid());
    mob.stopPathing();
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
    int now = mob.ticksLived();
    if (issued && now - issuedAt < Math.max(1, intervalTicks.getAsInt())) {
      return;
    }
    if (retarget) {
      retarget = false;
      mob.stopPathing();
    } else if (issued && !reissueWhilePathing && mob.hasPath()) {
      issuedAt = now;
      return;
    }
    issued = true;
    issuedAt = now;
    ISSUING.set(mob.uuid());
    try {
      mob.pathTo(destination, speed);
    } finally {
      ISSUING.remove();
    }
  }
}
