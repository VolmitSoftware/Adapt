package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Cached id to Bukkit registry value lookups. Returns null for unknown ids, never throws. */
public final class BukkitResolve {
  private static final Map<Id, Optional<Material>> MATERIALS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Attribute>> ATTRIBUTES = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<PotionEffectType>> EFFECTS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Enchantment>> ENCHANTMENTS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Sound>> SOUNDS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Particle>> PARTICLES = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<EntityType>> ENTITY_TYPES = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Biome>> BIOMES = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<PotionType>> POTIONS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<PatternType>> BANNER_PATTERNS = new ConcurrentHashMap<>();
  private static final Map<Material, Id> MATERIAL_IDS = new ConcurrentHashMap<>();

  private BukkitResolve() {
  }

  /** Air check without a registry round trip. */
  public static boolean isAir(Material material) {
    return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
  }

  public static Material material(Id id) {
    if (id == null) {
      return null;
    }
    return MATERIALS.computeIfAbsent(id, (Id missing) -> {
      Id resolved = ContentAliases.resolve(RegistryKind.ITEM, missing,
          (Id candidate) -> Material.matchMaterial(candidate.toString()) != null);
      return Optional.ofNullable(resolved == null ? null : Material.matchMaterial(resolved.toString()));
    }).orElse(null);
  }

  public static Id id(Material material) {
    if (material == null) {
      return null;
    }
    return MATERIAL_IDS.computeIfAbsent(material, (Material missing) -> modern(RegistryKind.ITEM, BukkitConversions.id(missing.getKey())));
  }

  public static Attribute attribute(Id id) {
    return lookup(ATTRIBUTES, RegistryKey.ATTRIBUTE, RegistryKind.ATTRIBUTE, id);
  }

  /** The 26.x id of an attribute. */
  public static Id id(Attribute attribute) {
    return modern(RegistryKind.ATTRIBUTE, id((Keyed) attribute));
  }

  public static PotionEffectType effect(Id id) {
    return lookup(EFFECTS, RegistryKey.MOB_EFFECT, RegistryKind.EFFECT, id);
  }

  public static Enchantment enchantment(Id id) {
    return lookup(ENCHANTMENTS, RegistryKey.ENCHANTMENT, RegistryKind.ENCHANTMENT, id);
  }

  public static Sound sound(Id id) {
    return lookup(SOUNDS, RegistryKey.SOUND_EVENT, RegistryKind.SOUND, id);
  }

  public static Particle particle(Id id) {
    return lookup(PARTICLES, RegistryKey.PARTICLE_TYPE, RegistryKind.PARTICLE, id);
  }

  public static EntityType entityType(Id id) {
    return lookup(ENTITY_TYPES, RegistryKey.ENTITY_TYPE, RegistryKind.ENTITY_TYPE, id);
  }

  public static Biome biome(Id id) {
    return lookup(BIOMES, RegistryKey.BIOME, RegistryKind.BIOME, id);
  }

  public static PotionType potion(Id id) {
    return lookup(POTIONS, RegistryKey.POTION, RegistryKind.POTION, id);
  }

  public static PatternType bannerPattern(Id id) {
    return lookup(BANNER_PATTERNS, RegistryKey.BANNER_PATTERN, null, id);
  }

  /** Registry id of a keyed value, or null for values without a key (such as the unknown entity type). */
  public static Id id(Keyed keyed) {
    if (keyed == null) {
      return null;
    }
    try {
      return BukkitConversions.id(keyed.getKey());
    } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException keyless) {
      return null;
    }
  }

  private static <T extends Keyed> T lookup(Map<Id, Optional<T>> cache, RegistryKey<T> key, RegistryKind kind, Id id) {
    if (id == null) {
      return null;
    }
    Optional<T> cached = cache.get(id);
    if (cached != null) {
      return cached.orElse(null);
    }
    T value;
    try {
      Registry<T> registry = RegistryAccess.registryAccess().getRegistry(key);
      Id resolved = kind == null ? id
          : ContentAliases.resolve(kind, id, (Id candidate) -> registry.get(BukkitConversions.key(candidate)) != null);
      value = resolved == null ? null : registry.get(BukkitConversions.key(resolved));
    } catch (RuntimeException unavailable) {
      return null;
    }
    cache.put(id, Optional.ofNullable(value));
    return value;
  }

  private static Id modern(RegistryKind kind, Id nativeId) {
    List<Id> modern = ContentAliases.modern(kind, nativeId);
    return modern.isEmpty() ? nativeId : modern.get(0);
  }
}
