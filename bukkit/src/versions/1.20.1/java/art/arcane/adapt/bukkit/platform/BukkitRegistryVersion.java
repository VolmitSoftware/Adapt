package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.natives.ServerEffects;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Registry access through the Bukkit registries of Paper 1.20.1. Items and blocks share the material registry; tags
 * exist for blocks, items and entity types; damage types have no API and read as an empty registry.
 */
final class BukkitRegistryVersion {
  private BukkitRegistryVersion() {
  }

  static boolean exists(RegistryKind kind, Id id) {
    if (kind == null || id == null) {
      return false;
    }
    NamespacedKey key = BukkitConversions.key(id);
    return switch (kind) {
      case ITEM -> Registry.MATERIAL.get(key) instanceof Material material && material.isItem();
      case BLOCK -> Registry.MATERIAL.get(key) instanceof Material material && material.isBlock();
      case ENTITY_TYPE -> Registry.ENTITY_TYPE.get(key) != null;
      case SOUND -> Registry.SOUNDS.get(key) != null;
      case PARTICLE -> BukkitResolve.particle(id) != null;
      case EFFECT -> Registry.POTION_EFFECT_TYPE.get(key) != null;
      case ENCHANTMENT -> Registry.ENCHANTMENT.get(key) != null;
      case ATTRIBUTE -> Registry.ATTRIBUTE.get(key) != null;
      case BIOME -> Registry.BIOME.get(key) != null;
      case DAMAGE_TYPE -> false;
      case POTION -> BukkitResolve.potionData(id) != null;
    };
  }

  static Set<Id> ids(RegistryKind kind) {
    Set<Id> ids = new LinkedHashSet<>();
    if (kind == null) {
      return ids;
    }
    switch (kind) {
      case ITEM, BLOCK -> {
        for (Material material : Registry.MATERIAL) {
          if (kind == RegistryKind.ITEM ? material.isItem() : material.isBlock()) {
            ids.add(BukkitConversions.id(material.getKey()));
          }
        }
      }
      case ENTITY_TYPE -> collect(Registry.ENTITY_TYPE, ids);
      case SOUND -> collect(Registry.SOUNDS, ids);
      case PARTICLE -> {
        for (Particle particle : Particle.values()) {
          Id id = BukkitResolve.id(particle);
          if (id != null) {
            ids.add(id);
          }
        }
      }
      case EFFECT -> collect(Registry.POTION_EFFECT_TYPE, ids);
      case ENCHANTMENT -> collect(Registry.ENCHANTMENT, ids);
      case ATTRIBUTE -> collect(Registry.ATTRIBUTE, ids);
      case BIOME -> collect(Registry.BIOME, ids);
      case DAMAGE_TYPE -> {
      }
      case POTION -> {
        for (PotionType type : PotionType.values()) {
          addPotion(ids, new PotionData(type));
          if (type.isExtendable()) {
            addPotion(ids, new PotionData(type, true, false));
          }
          if (type.isUpgradeable()) {
            addPotion(ids, new PotionData(type, false, true));
          }
        }
      }
    }
    return ids;
  }

  static boolean isTagged(RegistryKind kind, Id tag, Id value) {
    if (tag == null || value == null) {
      return false;
    }
    NamespacedKey key = BukkitConversions.key(value);
    return switch (kind) {
      case ITEM -> Registry.MATERIAL.get(key) instanceof Material material
          && tag(Tag.REGISTRY_ITEMS, tag, Material.class) instanceof Tag<Material> items && items.isTagged(material);
      case BLOCK -> Registry.MATERIAL.get(key) instanceof Material material
          && tag(Tag.REGISTRY_BLOCKS, tag, Material.class) instanceof Tag<Material> blocks && blocks.isTagged(material);
      case ENTITY_TYPE -> Registry.ENTITY_TYPE.get(key) instanceof EntityType type
          && tag(Tag.REGISTRY_ENTITY_TYPES, tag, EntityType.class) instanceof Tag<EntityType> types && types.isTagged(type);
      default -> false;
    };
  }

  static Set<Id> tagValues(RegistryKind kind, Id tag) {
    if (kind == null || tag == null) {
      return Set.of();
    }
    Tag<? extends Keyed> resolved = switch (kind) {
      case ITEM -> tag(Tag.REGISTRY_ITEMS, tag, Material.class);
      case BLOCK -> tag(Tag.REGISTRY_BLOCKS, tag, Material.class);
      case ENTITY_TYPE -> tag(Tag.REGISTRY_ENTITY_TYPES, tag, EntityType.class);
      default -> null;
    };
    if (resolved == null) {
      return Set.of();
    }
    Set<Id> ids = new LinkedHashSet<>();
    for (Keyed value : resolved.getValues()) {
      ids.add(BukkitConversions.id(value.getKey()));
    }
    return Set.copyOf(ids);
  }

  static String particleDataType(Id particle) {
    Particle resolved = BukkitResolve.particle(particle);
    return resolved == null || resolved.getDataType() == Void.class ? null : resolved.getDataType().getSimpleName();
  }

  static EffectCategory effectCategory(Id effect) {
    PotionEffectType type = effect == null ? null : Registry.POTION_EFFECT_TYPE.get(BukkitConversions.key(effect));
    if (type == null) {
      return null;
    }
    return switch (type.getEffectCategory()) {
      case BENEFICIAL -> EffectCategory.BENEFICIAL;
      case HARMFUL -> EffectCategory.HARMFUL;
      case NEUTRAL -> EffectCategory.NEUTRAL;
    };
  }

  static boolean isInstantEffect(Id effect) {
    PotionEffectType type = effect == null ? null : Registry.POTION_EFFECT_TYPE.get(BukkitConversions.key(effect));
    return type != null && type.isInstant();
  }

  @SuppressWarnings("deprecation")
  static List<EffectInstance> potionEffects(Id potion) {
    PotionData data = BukkitResolve.potionData(potion);
    if (data == null) {
      return List.of();
    }
    List<EffectInstance> effects = new ArrayList<>();
    for (PotionEffect effect : Bukkit.getPotionBrewer().getEffects(data.getType(), data.isUpgraded(), data.isExtended())) {
      effects.add(new EffectInstance(BukkitResolve.id(effect.getType()),
          effect.isInfinite() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier(), effect.isAmbient(),
          effect.hasParticles(), effect.hasIcon()));
    }
    return effects;
  }

  static List<EffectModifier> effectModifiers(Id effect, int amplifier) {
    PotionEffectType type = effect == null ? null : Registry.POTION_EFFECT_TYPE.get(BukkitConversions.key(effect));
    if (type == null) {
      return List.of();
    }
    List<EffectModifier> modifiers = new ArrayList<>();
    for (ServerEffects.Modifier modifier : ServerEffects.modifiers(type.getKey(), amplifier)) {
      Attribute attribute = Registry.ATTRIBUTE.get(modifier.attribute());
      if (attribute != null) {
        modifiers.add(new EffectModifier(BukkitResolve.id(attribute),
            BukkitConversions.operation(AttributeModifier.Operation.values()[modifier.operation()]), modifier.amount()));
      }
    }
    return modifiers;
  }

  private static <T extends Keyed> void collect(Registry<T> registry, Set<Id> ids) {
    for (T value : registry) {
      ids.add(BukkitConversions.id(value.getKey()));
    }
  }

  private static void addPotion(Set<Id> ids, PotionData data) {
    Id id = BukkitResolve.id(data);
    if (id != null) {
      ids.add(id);
    }
  }

  private static <T extends Keyed> Tag<T> tag(String registry, Id tag, Class<T> type) {
    try {
      return Bukkit.getTag(registry, BukkitConversions.key(tag), type);
    } catch (RuntimeException unavailable) {
      return null;
    }
  }
}
