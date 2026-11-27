package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Objects;

/**
 * Namespaced identifier for materials, sounds, particles, effects, enchantments, entity types,
 * attributes, biomes, recipes, advancements and data keys. Follows vanilla resource location rules:
 * namespace {@code [a-z0-9_.-]}, path {@code [a-z0-9_.-/]}. The constructor throws
 * {@link IllegalArgumentException} for an empty or invalid part.
 *
 * @param namespace the part before the colon, such as {@code minecraft} or {@code adapt}
 * @param path the part after the colon, such as {@code diamond_sword}
 */
@ApiStatus.AvailableSince("1.0.0")
public record Id(String namespace, String path) implements Comparable<Id> {
  /** The vanilla namespace. */
  public static final String MINECRAFT = "minecraft";
  /** Adapt's own namespace. */
  public static final String ADAPT = "adapt";

  public Id {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(path, "path");
    if (namespace.isEmpty() || !validNamespace(namespace)) {
      throw new IllegalArgumentException("Invalid id namespace: '" + namespace + "'");
    }
    if (path.isEmpty() || !validPath(path)) {
      throw new IllegalArgumentException("Invalid id path: '" + path + "'");
    }
  }

  /** Same as the constructor. */
  public static Id of(String namespace, String path) {
    return new Id(namespace, path);
  }

  /** An id in the {@code minecraft} namespace. */
  public static Id minecraft(String path) {
    return new Id(MINECRAFT, path);
  }

  /** An id in the {@code adapt} namespace. */
  public static Id adapt(String path) {
    return new Id(ADAPT, path);
  }

  /**
   * Parses {@code namespace:path}; a bare path gets the {@code minecraft} namespace. Throws
   * {@link IllegalArgumentException} when the text is not a valid id.
   */
  public static Id parse(String value) {
    return parse(value, MINECRAFT);
  }

  /**
   * Parses user input such as a command argument or a config value: {@code namespace:path}, where a bare path gets
   * the {@code adapt} namespace.
   */
  public static Id parseAdapt(String value) {
    return parse(value, ADAPT);
  }

  /** Same as {@link #parse(String)} but returns null instead of throwing, and for null or empty text. */
  public static Id tryParse(String value) {
    return tryParse(value, MINECRAFT);
  }

  /** Same as {@link #parseAdapt(String)} but returns null instead of throwing, and for null or empty text. */
  public static Id tryParseAdapt(String value) {
    return tryParse(value, ADAPT);
  }

  private static Id parse(String value, String defaultNamespace) {
    Objects.requireNonNull(value, "value");
    int colon = value.indexOf(':');
    if (colon < 0) {
      return new Id(defaultNamespace, value);
    }
    if (value.indexOf(':', colon + 1) >= 0) {
      throw new IllegalArgumentException("Invalid id: '" + value + "'");
    }
    String namespace = colon == 0 ? defaultNamespace : value.substring(0, colon);
    return new Id(namespace, value.substring(colon + 1));
  }

  private static Id tryParse(String value, String defaultNamespace) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return parse(value, defaultNamespace);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  /**
   * Converts a legacy constant name such as {@code DIAMOND_SWORD} or {@code minecraft:Diamond_Sword}
   * into an id by lower-casing it. Used to read enum-based config values.
   */
  public static Id fromLegacyName(String value) {
    Objects.requireNonNull(value, "value");
    return parse(value.trim().toLowerCase(Locale.ROOT));
  }

  /** True when the namespace is {@code minecraft}. */
  public boolean isMinecraft() {
    return MINECRAFT.equals(namespace);
  }

  /** True for the three vanilla air blocks. */
  public boolean isAir() {
    return isMinecraft() && (path.equals("air") || path.equals("cave_air") || path.equals("void_air"));
  }

  /** Upper-case legacy constant form of the path, e.g. {@code DIAMOND_SWORD}. */
  public String legacyName() {
    return path.replace('/', '_').replace('.', '_').toUpperCase(Locale.ROOT);
  }

  /** An id in the same namespace with another path. */
  public Id withPath(String newPath) {
    return new Id(namespace, newPath);
  }

  /** Orders by namespace, then by path. */
  @Override
  public int compareTo(Id other) {
    int namespaceOrder = namespace.compareTo(other.namespace);
    return namespaceOrder != 0 ? namespaceOrder : path.compareTo(other.path);
  }

  /** The {@code namespace:path} form, which {@link #parse(String)} reads back. */
  @Override
  public String toString() {
    return namespace + ":" + path;
  }

  private static boolean validNamespace(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '.' && c != '-') {
        return false;
      }
    }
    return true;
  }

  private static boolean validPath(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '.' && c != '-' && c != '/') {
        return false;
      }
    }
    return true;
  }
}
