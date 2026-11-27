package art.arcane.adapt.api.asset;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * One file of an {@link AssetBundle}.
 *
 * @param kind what the file is used for
 * @param id   the id content refers to it by; for a texture this is the path below {@code textures/} without the
 *             extension, so the sprite {@code alchemist:gui/skill/alchemy} is the file
 *             {@code textures/gui/skill/alchemy.png}
 * @param path the file's path below the bundle root, with {@code /} separators
 */
@ApiStatus.Experimental
public record AssetEntry(AssetKind kind, Id id, String path) {
  public AssetEntry {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(path, "path");
    if (path.isEmpty() || path.startsWith("/") || path.contains("..") || path.contains("\\")) {
      throw new IllegalArgumentException("Invalid asset path: '" + path + "'");
    }
  }
}
