package art.arcane.adapt.platform;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Vanilla ids that 26.x renamed, for adapters running an older Minecraft version. Core always speaks the 26.x id; an
 * adapter whose registry lacks it looks up the older name here ({@link #resolve}, {@link #resolveFx} for sounds and
 * particles) and maps native ids back with {@link #modern}. On 26.x every modern id exists, so the table is never
 * consulted there.
 *
 * <p>Several modern ids can share one older name (every boat type was {@code minecraft:boat} before 1.21.2, both
 * thrown potions were {@code minecraft:potion} before 1.21.5); {@link #modern} returns all candidates and the adapter
 * picks by the native object.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class ContentAliases {
  private static final Map<RegistryKind, Map<Id, List<Id>>> LEGACY = new EnumMap<>(RegistryKind.class);
  private static final Map<RegistryKind, Map<Id, List<Id>>> MODERN = new EnumMap<>(RegistryKind.class);
  private static final Map<RegistryKind, Map<Id, Id>> FX_SUBSTITUTES = Map.of(
      RegistryKind.SOUND, Map.of(
          Id.minecraft("item.lead.break"), Id.minecraft("entity.leash_knot.break"),
          Id.minecraft("item.lead.tied"), Id.minecraft("entity.leash_knot.place"),
          Id.minecraft("entity.copper_golem.item_drop"), Id.minecraft("entity.item.pickup"),
          Id.minecraft("entity.wind_charge.wind_burst"), Id.minecraft("entity.generic.explode")),
      RegistryKind.PARTICLE, Map.of(
          Id.minecraft("gust"), Id.minecraft("poof")));
  private static final Map<Id, Id> MOUNT_ATTRIBUTES = Map.of(Id.minecraft("jump_strength"), Id.minecraft("horse.jump_strength"));

  static {
    attribute("generic.", "armor", "armor_toughness", "attack_damage", "attack_knockback", "attack_speed", "burning_time",
        "explosion_knockback_resistance", "fall_damage_multiplier", "flying_speed", "follow_range", "gravity",
        "jump_strength", "knockback_resistance", "luck", "max_absorption", "max_health", "movement_efficiency",
        "movement_speed", "oxygen_bonus", "safe_fall_distance", "scale", "step_height", "water_movement_efficiency");
    attribute("player.", "block_break_speed", "block_interaction_range", "entity_interaction_range", "mining_efficiency",
        "sneaking_speed", "submerged_mining_speed", "sweeping_damage_ratio");
    attribute("zombie.", "spawn_reinforcements");
    block("short_grass", "grass");
    block("iron_chain", "chain");
    alias(RegistryKind.ITEM, "turtle_scute", "scute");
    alias(RegistryKind.ENCHANTMENT, "sweeping_edge", "sweeping");
    alias(RegistryKind.ENTITY_TYPE, "splash_potion", "potion");
    alias(RegistryKind.ENTITY_TYPE, "lingering_potion", "potion");
    for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "cherry", "dark_oak", "mangrove")) {
      alias(RegistryKind.ENTITY_TYPE, wood + "_boat", "boat");
      alias(RegistryKind.ENTITY_TYPE, wood + "_chest_boat", "chest_boat");
    }
    alias(RegistryKind.ENTITY_TYPE, "bamboo_raft", "boat");
    alias(RegistryKind.ENTITY_TYPE, "bamboo_chest_raft", "chest_boat");
    for (Map.Entry<Id, Id> mount : MOUNT_ATTRIBUTES.entrySet()) {
      MODERN.computeIfAbsent(RegistryKind.ATTRIBUTE, (RegistryKind missing) -> new LinkedHashMap<>())
          .computeIfAbsent(mount.getValue(), (Id missing) -> new ArrayList<>()).add(mount.getKey());
    }
  }

  private ContentAliases() {
  }

  /** Older names of a 26.x id, newest first; empty when it was never renamed. */
  public static List<Id> legacyIds(RegistryKind kind, Id modern) {
    Map<Id, List<Id>> table = LEGACY.get(kind);
    List<Id> ids = table == null || modern == null ? null : table.get(modern);
    return ids == null ? List.of() : Collections.unmodifiableList(ids);
  }

  /** 26.x ids an older name stands for; empty when the name was never renamed. */
  public static List<Id> modern(RegistryKind kind, Id legacy) {
    Map<Id, List<Id>> table = MODERN.get(kind);
    List<Id> ids = table == null || legacy == null ? null : table.get(legacy);
    return ids == null ? List.of() : Collections.unmodifiableList(ids);
  }

  /** The id itself when {@code present} accepts it, else its first accepted older name, else null. */
  public static Id resolve(RegistryKind kind, Id modern, Predicate<Id> present) {
    if (modern == null) {
      return null;
    }
    if (present.test(modern)) {
      return modern;
    }
    for (Id legacy : legacyIds(kind, modern)) {
      if (present.test(legacy)) {
        return legacy;
      }
    }
    return null;
  }

  /**
   * Like {@link #resolve}, and for a sound or particle that has no older name, a similar one that older versions have
   * ({@code item.lead.break} plays {@code entity.leash_knot.break}). Null when nothing fits: skip the effect.
   */
  public static Id resolveFx(RegistryKind kind, Id modern, Predicate<Id> present) {
    Id resolved = resolve(kind, modern, present);
    if (resolved != null) {
      return resolved;
    }
    Map<Id, Id> substitutes = FX_SUBSTITUTES.get(kind);
    Id substitute = substitutes == null ? null : substitutes.get(modern);
    return substitute != null && present.test(substitute) ? substitute : null;
  }

  /**
   * Attribute name that only mounts carried before it became generic ({@code horse.jump_strength} for
   * {@code jump_strength} on 1.20.1), or null. Players and other mobs do not have it on that version, so it is no
   * {@link #legacyIds} entry; {@link #modern} still maps the mount name back.
   */
  public static Id mountAttribute(Id modern) {
    return modern == null ? null : MOUNT_ATTRIBUTES.get(modern);
  }

  private static void attribute(String prefix, String... names) {
    for (String name : names) {
      alias(RegistryKind.ATTRIBUTE, name, prefix + name);
    }
  }

  private static void block(String modern, String legacy) {
    alias(RegistryKind.ITEM, modern, legacy);
    alias(RegistryKind.BLOCK, modern, legacy);
  }

  private static void alias(RegistryKind kind, String modern, String legacy) {
    Id modernId = Id.minecraft(modern);
    Id legacyId = Id.minecraft(legacy);
    LEGACY.computeIfAbsent(kind, (RegistryKind missing) -> new LinkedHashMap<>())
        .computeIfAbsent(modernId, (Id missing) -> new ArrayList<>()).add(legacyId);
    MODERN.computeIfAbsent(kind, (RegistryKind missing) -> new LinkedHashMap<>())
        .computeIfAbsent(legacyId, (Id missing) -> new ArrayList<>()).add(modernId);
  }
}
