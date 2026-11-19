package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
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
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached id to Bukkit registry value lookups for Paper 1.20.1. Returns null for unknown ids, never throws. Particles,
 * potions and banner patterns are enums without registry keys there, so they map through their 26.x ids by name; a
 * potion id carries its long or strong variant, which 1.20.1 keeps in {@link PotionData}.
 */
public final class BukkitResolve {
  private static final Map<Id, Optional<Material>> MATERIALS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Attribute>> ATTRIBUTES = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<PotionEffectType>> EFFECTS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Enchantment>> ENCHANTMENTS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Sound>> SOUNDS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Particle>> PARTICLES = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<EntityType>> ENTITY_TYPES = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Biome>> BIOMES = new ConcurrentHashMap<>();
  private static final Map<Material, Id> MATERIAL_IDS = new ConcurrentHashMap<>();
  private static final Map<PotionType, String> POTION_NAMES = Map.of(PotionType.JUMP, "leaping", PotionType.SPEED, "swiftness",
      PotionType.INSTANT_HEAL, "healing", PotionType.INSTANT_DAMAGE, "harming", PotionType.REGEN, "regeneration");
  private static final Map<Particle, String> PARTICLE_NAMES = Map.ofEntries(
      Map.entry(Particle.BLOCK_CRACK, "block"),
      Map.entry(Particle.CRIT_MAGIC, "enchanted_hit"),
      Map.entry(Particle.DRIP_LAVA, "dripping_lava"),
      Map.entry(Particle.DRIP_WATER, "dripping_water"),
      Map.entry(Particle.ENCHANTMENT_TABLE, "enchant"),
      Map.entry(Particle.EXPLOSION_HUGE, "explosion_emitter"),
      Map.entry(Particle.EXPLOSION_LARGE, "explosion"),
      Map.entry(Particle.EXPLOSION_NORMAL, "poof"),
      Map.entry(Particle.FIREWORKS_SPARK, "firework"),
      Map.entry(Particle.ITEM_CRACK, "item"),
      Map.entry(Particle.MOB_APPEARANCE, "elder_guardian"),
      Map.entry(Particle.REDSTONE, "dust"),
      Map.entry(Particle.SLIME, "item_slime"),
      Map.entry(Particle.SMOKE_LARGE, "large_smoke"),
      Map.entry(Particle.SMOKE_NORMAL, "smoke"),
      Map.entry(Particle.SNOWBALL, "item_snowball"),
      Map.entry(Particle.SPELL, "effect"),
      Map.entry(Particle.SPELL_INSTANT, "instant_effect"),
      Map.entry(Particle.SPELL_MOB, "entity_effect"),
      Map.entry(Particle.SPELL_MOB_AMBIENT, "ambient_entity_effect"),
      Map.entry(Particle.SPELL_WITCH, "witch"),
      Map.entry(Particle.SUSPENDED, "underwater"),
      Map.entry(Particle.TOTEM, "totem_of_undying"),
      Map.entry(Particle.TOWN_AURA, "mycelium"),
      Map.entry(Particle.VILLAGER_ANGRY, "angry_villager"),
      Map.entry(Particle.VILLAGER_HAPPY, "happy_villager"),
      Map.entry(Particle.WATER_BUBBLE, "bubble"),
      Map.entry(Particle.WATER_DROP, "rain"),
      Map.entry(Particle.WATER_SPLASH, "splash"),
      Map.entry(Particle.WATER_WAKE, "fishing"));
  private static final Set<Particle> PARTICLE_DUPLICATES = Set.of(Particle.BLOCK_DUST, Particle.SNOW_SHOVEL,
      Particle.SUSPENDED_DEPTH, Particle.LEGACY_BLOCK_CRACK, Particle.LEGACY_BLOCK_DUST, Particle.LEGACY_FALLING_DUST);
  private static final Map<PatternType, String> PATTERN_NAMES = Map.of(PatternType.STRIPE_SMALL, "small_stripes",
      PatternType.DIAGONAL_LEFT_MIRROR, "diagonal_up_left", PatternType.DIAGONAL_RIGHT_MIRROR, "diagonal_up_right",
      PatternType.CIRCLE_MIDDLE, "circle", PatternType.RHOMBUS_MIDDLE, "rhombus",
      PatternType.HALF_VERTICAL_MIRROR, "half_vertical_right", PatternType.HALF_HORIZONTAL_MIRROR, "half_horizontal_bottom");

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
    return lookup(ATTRIBUTES, Registry.ATTRIBUTE, RegistryKind.ATTRIBUTE, id);
  }

  /** The 26.x id of an attribute. */
  public static Id id(Attribute attribute) {
    return modern(RegistryKind.ATTRIBUTE, id((Keyed) attribute));
  }

  public static PotionEffectType effect(Id id) {
    return lookup(EFFECTS, Registry.POTION_EFFECT_TYPE, RegistryKind.EFFECT, id);
  }

  public static Enchantment enchantment(Id id) {
    return lookup(ENCHANTMENTS, Registry.ENCHANTMENT, RegistryKind.ENCHANTMENT, id);
  }

  public static Sound sound(Id id) {
    return lookup(SOUNDS, Registry.SOUNDS, RegistryKind.SOUND, id);
  }

  public static Particle particle(Id id) {
    if (id == null) {
      return null;
    }
    return PARTICLES.computeIfAbsent(id, (Id missing) -> {
      for (Particle particle : Particle.values()) {
        if (missing.equals(id(particle))) {
          return Optional.of(particle);
        }
      }
      return Optional.empty();
    }).orElse(null);
  }

  /** The vanilla id of a particle, or null for the duplicate and legacy constants that have none of their own. */
  public static Id id(Particle particle) {
    if (particle == null || PARTICLE_DUPLICATES.contains(particle)) {
      return null;
    }
    return Id.minecraft(PARTICLE_NAMES.getOrDefault(particle, particle.name().toLowerCase(Locale.ROOT)));
  }

  public static EntityType entityType(Id id) {
    return lookup(ENTITY_TYPES, Registry.ENTITY_TYPE, RegistryKind.ENTITY_TYPE, id);
  }

  public static Biome biome(Id id) {
    return lookup(BIOMES, Registry.BIOME, RegistryKind.BIOME, id);
  }

  public static PotionType potion(Id id) {
    PotionData data = potionData(id);
    return data == null ? null : data.getType();
  }

  /** The potion type and its long or strong variant named by a 26.x potion id. */
  public static PotionData potionData(Id id) {
    if (id == null || !Id.MINECRAFT.equals(id.namespace())) {
      return null;
    }
    String path = id.path();
    boolean extended = path.startsWith("long_");
    boolean upgraded = path.startsWith("strong_");
    String name = extended ? path.substring(5) : upgraded ? path.substring(7) : path;
    for (PotionType type : PotionType.values()) {
      if (potionName(type).equals(name)) {
        if ((extended && !type.isExtendable()) || (upgraded && !type.isUpgradeable())) {
          return null;
        }
        return new PotionData(type, extended, upgraded);
      }
    }
    return null;
  }

  /** The 26.x id of a potion, or null for the uncraftable potion that id space does not have. */
  public static Id id(PotionData data) {
    if (data == null || data.getType() == PotionType.UNCRAFTABLE) {
      return null;
    }
    String name = potionName(data.getType());
    return Id.minecraft(data.isExtended() ? "long_" + name : data.isUpgraded() ? "strong_" + name : name);
  }

  /** The 26.x id of a potion type's plain variant. */
  public static Id id(PotionType type) {
    return type == null ? null : id(new PotionData(type));
  }

  public static PatternType bannerPattern(Id id) {
    if (id == null || !Id.MINECRAFT.equals(id.namespace())) {
      return null;
    }
    for (PatternType type : PatternType.values()) {
      if (patternName(type).equals(id.path())) {
        return type;
      }
    }
    return null;
  }

  public static Id id(PatternType type) {
    return type == null ? null : Id.minecraft(patternName(type));
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

  private static <T extends Keyed> T lookup(Map<Id, Optional<T>> cache, Registry<T> registry, RegistryKind kind, Id id) {
    if (id == null) {
      return null;
    }
    Optional<T> cached = cache.get(id);
    if (cached != null) {
      return cached.orElse(null);
    }
    T value;
    try {
      Id resolved = ContentAliases.resolve(kind, id, (Id candidate) -> registry.get(BukkitConversions.key(candidate)) != null);
      value = resolved == null ? null : registry.get(BukkitConversions.key(resolved));
    } catch (RuntimeException unavailable) {
      return null;
    }
    cache.put(id, Optional.ofNullable(value));
    return value;
  }

  private static String potionName(PotionType type) {
    return POTION_NAMES.getOrDefault(type, type.name().toLowerCase(Locale.ROOT));
  }

  private static String patternName(PatternType type) {
    return PATTERN_NAMES.getOrDefault(type, type.name().toLowerCase(Locale.ROOT));
  }

  private static Id modern(RegistryKind kind, Id nativeId) {
    List<Id> modern = ContentAliases.modern(kind, nativeId);
    return modern.isEmpty() ? nativeId : modern.get(0);
  }
}
