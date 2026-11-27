package art.arcane.adapt.api.item;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Item and block names from config files, read the way the Bukkit plugin read them, so the same configs keep
 * meaning the same blocks on every platform.
 */
@ApiStatus.Experimental
public final class MaterialNames {
  private static final String MINECRAFT_PREFIX = "minecraft:";
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern NON_WORD = Pattern.compile("\\W");
  private static final Pattern CONSTANT = Pattern.compile("[A-Z0-9_]+");

  private MaterialNames() {
  }

  /**
   * Bukkit {@code Material.matchMaterial}: an optional lower-case {@code minecraft:} prefix, then upper-cased with
   * whitespace runs as underscores and every other non-word character dropped. An unprefixed id in another namespace
   * that names an item or block on this server is taken as written. Returns null when the name cannot name anything;
   * the caller checks the registry.
   */
  public static @Nullable Id match(@Nullable String name) {
    if (name == null) {
      return null;
    }
    if (!name.startsWith(MINECRAFT_PREFIX)) {
      Id modded = moddedId(name);
      if (modded != null && AdaptApi.isRunning() && exists(modded)) {
        return modded;
      }
    }
    return vanilla(name);
  }

  /** {@link #match} without the modded-id case: exactly the name Bukkit {@code Material.matchMaterial} looks up. */
  public static @Nullable Id vanilla(@Nullable String name) {
    if (name == null) {
      return null;
    }
    String filtered = name.startsWith(MINECRAFT_PREFIX) ? name.substring(MINECRAFT_PREFIX.length()) : name;
    filtered = NON_WORD.matcher(WHITESPACE.matcher(filtered.toUpperCase(Locale.ROOT)).replaceAll("_")).replaceAll("");
    return filtered.isEmpty() ? null : Id.tryParse(MINECRAFT_PREFIX + filtered.toLowerCase(Locale.ROOT));
  }

  /** Bukkit {@code Material.getMaterial}: exactly the upper-case constant name, for example {@code ROTTEN_FLESH}. */
  public static @Nullable Id exact(@Nullable String name) {
    if (name == null || !CONSTANT.matcher(name).matches()) {
      return null;
    }
    return Id.tryParse(MINECRAFT_PREFIX + name.toLowerCase(Locale.ROOT));
  }

  /** True when the id names an item or a block on this server, as every Bukkit {@code Material} did. */
  public static boolean exists(@Nullable Id id) {
    if (id == null) {
      return false;
    }
    RegistryLookup registries = AdaptApi.platform().host().registries();
    return registries.exists(RegistryKind.ITEM, id) || registries.exists(RegistryKind.BLOCK, id);
  }

  /** A lower-case id in a namespace other than {@code minecraft}, as written, or null. */
  public static @Nullable Id modded(@Nullable String name) {
    return name == null ? null : moddedId(name);
  }

  private static @Nullable Id moddedId(String name) {
    int colon = name.indexOf(':');
    if (colon <= 0 || name.substring(0, colon).toLowerCase(Locale.ROOT).equals("minecraft")) {
      return null;
    }
    return Id.tryParse(name);
  }
}
