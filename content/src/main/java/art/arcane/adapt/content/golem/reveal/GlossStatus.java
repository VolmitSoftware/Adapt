package art.arcane.adapt.content.golem.reveal;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.integration.GlossOverlay;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.adaptation.discovery.EntityInsight;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class GlossStatus {
  private static final int HOLD_POLLS = 3;
  private final Predicate<UUID> shown;
  private final Map<UUID, UUID> pushes = new ConcurrentHashMap<>();

  GlossStatus(Predicate<UUID> shown) {
    this.shown = Objects.requireNonNull(shown);
  }

  void push(PlayerRef viewer, List<Target> targets, long pollMillis) {
    UUID viewerId = viewer.uuid();
    if (EntityInsight.showing(viewerId)) {
      pushes.remove(viewerId);
      return;
    }
    Target target = lookedAt(viewer, targets);
    if (target == null || GolemancyRuntime.names().isEmpty()) {
      release(viewerId);
      return;
    }
    if (!AdaptApi.scheduler().runEntity(target.entity(), () -> send(viewer, target, pollMillis * HOLD_POLLS))) {
      release(viewerId);
    }
  }

  void release(UUID viewerId) {
    if (pushes.remove(viewerId) == null || EntityInsight.showing(viewerId)) {
      return;
    }
    if (GolemancyRuntime.names().isPresent()) {
      GlossOverlay.clear(viewerId);
    }
  }

  void forget(UUID viewerId) {
    pushes.remove(viewerId);
  }

  void clear() {
    pushes.clear();
  }

  private void send(PlayerRef viewer, Target target, long holdMillis) {
    LivingRef entity = target.entity();
    UUID viewerId = viewer.uuid();
    if (entity.isValid() && shown.test(viewerId) && GlossOverlay.show(viewer, entity, GolemLabel.legacyLines(target.content().get()), holdMillis)) {
      pushes.put(viewerId, entity.uuid());
    }
  }

  private static Target lookedAt(PlayerRef viewer, List<Target> targets) {
    if (targets.isEmpty()) {
      return null;
    }
    Loc eye = viewer.eyeLocation();
    Vec3 origin = eye.position();
    Vec3 direction = eye.direction();
    Target nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Target target : targets) {
      double distance = hitDistance(target.entity().boundingBox(), origin, direction, target.range());
      if (distance >= 0D && distance < nearestDistance) {
        nearest = target;
        nearestDistance = distance;
      }
    }
    if (nearest == null) {
      return null;
    }
    return viewer.world().rayTraceBlock(origin, direction, nearestDistance, false) == null ? nearest : null;
  }

  static double hitDistance(Box box, Vec3 origin, Vec3 direction, double maxDistance) {
    double length = direction.length();
    if (!(length > 0D)) {
      return -1D;
    }
    Vec3 unit = direction.multiply(1D / length);
    double enter = 0D;
    double exit = maxDistance;
    double[] start = {origin.x(), origin.y(), origin.z()};
    double[] step = {unit.x(), unit.y(), unit.z()};
    double[] min = {box.minX(), box.minY(), box.minZ()};
    double[] max = {box.maxX(), box.maxY(), box.maxZ()};
    for (int axis = 0; axis < 3; axis++) {
      if (Math.abs(step[axis]) < 1.0E-12D) {
        if (start[axis] < min[axis] || start[axis] > max[axis]) {
          return -1D;
        }
        continue;
      }
      double near = (min[axis] - start[axis]) / step[axis];
      double far = (max[axis] - start[axis]) / step[axis];
      if (near > far) {
        double swap = near;
        near = far;
        far = swap;
      }
      enter = Math.max(enter, near);
      exit = Math.min(exit, far);
      if (enter > exit) {
        return -1D;
      }
    }
    return enter;
  }

  record Target(LivingRef entity, double range, Supplier<GolemLabel.Content> content) {
  }
}
