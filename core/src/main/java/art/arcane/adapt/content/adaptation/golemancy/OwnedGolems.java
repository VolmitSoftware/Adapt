package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/** The player's loaded, awake bound golems near a point. */
public final class OwnedGolems {
  private OwnedGolems() {
  }

  /** The nearest awake golem of the player within {@code radius} of {@code center} that {@code accepts}. */
  public static @Nullable GolemHandle nearest(PlayerRef owner, Loc center, double radius, Predicate<GolemHandle> accepts) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return null;
    }
    double limit = radius * radius;
    GolemHandle nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (GolemHandle handle : registry.get().loadedOf(owner.uuid())) {
      if (handle.isRetired() || handle.isDormant() || !accepts.test(handle)) {
        continue;
      }
      MobRef golem = handle.entity();
      Loc at = golem == null || !golem.isValid() ? null : golem.location();
      if (at == null || !at.sameWorld(center)) {
        continue;
      }
      double distance = at.distanceSquared(center);
      if (distance <= limit && distance < nearestDistance) {
        nearest = handle;
        nearestDistance = distance;
      }
    }
    return nearest;
  }
}
