package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/**
 * A generated structure start: its registry key, its structure type key and the vanilla bounding box corners as
 * doubles (the same numbers Bukkit's {@code GeneratedStructure#getBoundingBox} reports).
 */
public record GeneratedStructure(Id structure, Id type, Box bounds) {
  public GeneratedStructure {
    Objects.requireNonNull(structure, "structure");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(bounds, "bounds");
  }
}
