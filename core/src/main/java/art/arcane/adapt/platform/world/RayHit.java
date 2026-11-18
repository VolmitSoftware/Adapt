package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Objects;

/** Result of a combined block and entity ray trace: exactly one of {@code block} and {@code entity} is set. */
public record RayHit(Vec3 position, BlockRef block, EntityRef entity) {
  public RayHit {
    Objects.requireNonNull(position, "position");
  }
}
