package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Result of a combined block and entity ray trace: exactly one of {@code block} and {@code entity} is set.
 *
 * @param position point where the ray hit
 * @param block block that was hit, or null
 * @param entity entity that was hit, or null
 */
@ApiStatus.AvailableSince("1.0.0")
public record RayHit(Vec3 position, BlockRef block, EntityRef entity) {
  public RayHit {
    Objects.requireNonNull(position, "position");
  }
}
