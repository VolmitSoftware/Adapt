package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.GolemNames;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.insight.EntityInsight;
import art.arcane.adapt.api.insight.GlossInsight;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

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

  void push(Player viewer, List<Target> targets, long pollMillis) {
    UUID viewerId = viewer.getUniqueId();
    if (EntityInsight.showing(viewerId)) {
      pushes.remove(viewerId);
      return;
    }
    Target target = lookedAt(viewer, targets);
    GlossInsight gloss = bridge();
    if (target == null || gloss == null) {
      release(viewerId);
      return;
    }
    if (!J.runEntity(target.entity(), () -> send(viewer, target, gloss, pollMillis * HOLD_POLLS))) {
      release(viewerId);
    }
  }

  void release(UUID viewerId) {
    if (pushes.remove(viewerId) == null || EntityInsight.showing(viewerId)) {
      return;
    }
    GlossInsight gloss = bridge();
    if (gloss != null) {
      gloss.clear(viewerId);
    }
  }

  void forget(UUID viewerId) {
    pushes.remove(viewerId);
  }

  void clear() {
    pushes.clear();
  }

  private void send(Player viewer, Target target, GlossInsight gloss, long holdMillis) {
    LivingEntity entity = target.entity();
    UUID viewerId = viewer.getUniqueId();
    if (entity.isValid() && shown.test(viewerId) && gloss.update(viewer, entity, GolemLabel.legacyLines(target.content().get()), holdMillis)) {
      pushes.put(viewerId, entity.getUniqueId());
    }
  }

  private static Target lookedAt(Player viewer, List<Target> targets) {
    if (targets.isEmpty()) {
      return null;
    }
    Location eye = viewer.getEyeLocation();
    Vector origin = eye.toVector();
    Vector direction = eye.getDirection();
    Target nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Target target : targets) {
      RayTraceResult hit = target.entity().getBoundingBox().rayTrace(origin, direction, target.range());
      if (hit == null) {
        continue;
      }
      double distance = origin.distance(hit.getHitPosition());
      if (distance < nearestDistance) {
        nearest = target;
        nearestDistance = distance;
      }
    }
    if (nearest == null) {
      return null;
    }
    RayTraceResult block = viewer.getWorld().rayTraceBlocks(eye, direction, nearestDistance, FluidCollisionMode.NEVER, true);
    return block == null ? nearest : null;
  }

  private static GlossInsight bridge() {
    return GolemancyRuntime.names().map(GolemNames::gloss).orElse(null);
  }

  record Target(LivingEntity entity, double range, Supplier<GolemLabel.Content> content) {
  }
}
