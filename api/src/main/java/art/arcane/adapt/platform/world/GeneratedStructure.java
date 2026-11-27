package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A generated structure start: its registry key, its structure type key and the vanilla bounding box corners as
 * doubles (the same numbers Bukkit's {@code GeneratedStructure#getBoundingBox} reports).
 *
 * @param structure structure registry key, for example {@code minecraft:village_plains}
 * @param type structure type key, for example {@code minecraft:jigsaw}
 * @param bounds bounding box of the whole structure in world coordinates
 */
@ApiStatus.AvailableSince("1.0.0")
public record GeneratedStructure(Id structure, Id type, Box bounds) {
  public GeneratedStructure {
    Objects.requireNonNull(structure, "structure");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(bounds, "bounds");
  }
}
