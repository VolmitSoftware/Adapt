package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.bukkit.block.banner.PatternType;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.ItemType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registries by value type. Paper goes through its registry API ({@link PaperRegistries}); Spigot uses Bukkit's
 * registry constants, since its server answers no registry lookup by class for some types (entity types among them),
 * and only has the item, block and entity type tags.
 */
public final class BukkitRegistries {
  private BukkitRegistries() {
  }

  /** The registry holding {@code type}, or null when the server has none. */
  public static <T extends Keyed> Registry<T> registry(Class<T> type) {
    try {
      return PaperApi.isPaper() ? PaperRegistries.registry(type) : SpigotRegistries.registry(type);
    } catch (RuntimeException unavailable) {
      return null;
    }
  }

  static <T extends Keyed> NamespacedKey key(Registry<T> registry, T value) {
    return PaperApi.isPaper() ? PaperRegistries.key(registry, value) : value.getKey();
  }

  public static <T extends Keyed> NamespacedKey keyOrThrow(Class<T> type, T value) {
    return PaperApi.isPaper() ? PaperRegistries.keyOrThrow(type, value) : value.getKey();
  }

  static <T extends Keyed> boolean isTagged(Class<T> type, Id tag, Id value) {
    if (type == null) {
      return false;
    }
    if (PaperApi.isPaper()) {
      return PaperRegistries.isTagged(type, tag, value);
    }
    return value != null && tagValues(type, tag).contains(value);
  }

  static <T extends Keyed> Set<Id> tagValues(Class<T> type, Id tag) {
    if (type == null || tag == null) {
      return Set.of();
    }
    if (PaperApi.isPaper()) {
      return PaperRegistries.tagValues(type, tag);
    }
    Tag<? extends Keyed> resolved = spigotTag(type, BukkitConversions.key(tag));
    if (resolved == null) {
      return Set.of();
    }
    Set<Id> ids = new LinkedHashSet<>();
    for (Keyed value : resolved.getValues()) {
      ids.add(BukkitConversions.id(value.getKey()));
    }
    return Set.copyOf(ids);
  }

  /** Spigot cannot read an effect's attribute modifiers, so effects report none there. */
  static List<EffectModifier> effectModifiers(PotionEffectType type, int amplifier) {
    return PaperApi.isPaper() ? PaperRegistries.effectModifiers(type, amplifier) : List.of();
  }

  static boolean hasDataComponents() {
    return !PaperApi.isPaper() || PaperRegistries.hasDataComponents();
  }

  @SuppressWarnings("removal")
  private static final class SpigotRegistries {
    private static final Map<Class<? extends Keyed>, Registry<? extends Keyed>> REGISTRIES = registries();

    @SuppressWarnings("unchecked")
    private static <T extends Keyed> Registry<T> registry(Class<T> type) {
      Registry<? extends Keyed> constant = REGISTRIES.get(type);
      return constant != null ? (Registry<T>) constant : Bukkit.getRegistry(type);
    }

    private static Map<Class<? extends Keyed>, Registry<? extends Keyed>> registries() {
      Map<Class<? extends Keyed>, Registry<? extends Keyed>> registries = new HashMap<>();
      registries.put(ItemType.class, Registry.ITEM);
      registries.put(BlockType.class, Registry.BLOCK);
      registries.put(EntityType.class, Registry.ENTITY_TYPE);
      registries.put(Sound.class, Registry.SOUNDS);
      registries.put(Particle.class, Registry.PARTICLE_TYPE);
      registries.put(PotionEffectType.class, Registry.EFFECT);
      registries.put(Enchantment.class, Registry.ENCHANTMENT);
      registries.put(Attribute.class, Registry.ATTRIBUTE);
      registries.put(Biome.class, Registry.BIOME);
      registries.put(DamageType.class, Registry.DAMAGE_TYPE);
      registries.put(PotionType.class, Registry.POTION);
      registries.put(PatternType.class, Registry.BANNER_PATTERN);
      registries.put(Structure.class, Registry.STRUCTURE);
      registries.put(StructureType.class, Registry.STRUCTURE_TYPE);
      return registries;
    }
  }

  private static Tag<? extends Keyed> spigotTag(Class<? extends Keyed> type, NamespacedKey key) {
    try {
      if (type == ItemType.class) {
        return Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
      }
      if (type == BlockType.class) {
        return Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
      }
      if (type == EntityType.class) {
        return Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class);
      }
      return null;
    } catch (RuntimeException unavailable) {
      return null;
    }
  }
}
