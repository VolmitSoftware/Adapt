package art.arcane.adapt.platform.value;

import java.util.Locale;
import java.util.Objects;

/**
 * Namespaced identifier for materials, sounds, particles, effects, enchantments, entity types,
 * attributes, biomes, recipes, advancements and data keys. Follows vanilla resource location rules:
 * namespace {@code [a-z0-9_.-]}, path {@code [a-z0-9_.-/]}.
 */
public record Id(String namespace, String path) implements Comparable<Id> {
  public static final String MINECRAFT = "minecraft";
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

  public static Id of(String namespace, String path) {
    return new Id(namespace, path);
  }

  public static Id minecraft(String path) {
    return new Id(MINECRAFT, path);
  }

  public static Id adapt(String path) {
    return new Id(ADAPT, path);
  }

  /** Parses {@code namespace:path}; a bare path gets the {@code minecraft} namespace. */
  public static Id parse(String value) {
    Objects.requireNonNull(value, "value");
    int colon = value.indexOf(':');
    if (colon < 0) {
      return new Id(MINECRAFT, value);
    }
    if (value.indexOf(':', colon + 1) >= 0) {
      throw new IllegalArgumentException("Invalid id: '" + value + "'");
    }
    String namespace = colon == 0 ? MINECRAFT : value.substring(0, colon);
    return new Id(namespace, value.substring(colon + 1));
  }

  /** Same as {@link #parse(String)} but returns null instead of throwing. */
  public static Id tryParse(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return parse(value);
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

  public Id withPath(String newPath) {
    return new Id(namespace, newPath);
  }

  @Override
  public int compareTo(Id other) {
    int namespaceOrder = namespace.compareTo(other.namespace);
    return namespaceOrder != 0 ? namespaceOrder : path.compareTo(other.path);
  }

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
