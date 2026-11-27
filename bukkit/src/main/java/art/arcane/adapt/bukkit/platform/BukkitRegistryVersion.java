package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry lookups by kind, through {@link BukkitRegistries}. */
final class BukkitRegistryVersion {
  private static final Map<RegistryKind, Class<? extends Keyed>> TYPES = types();

  private BukkitRegistryVersion() {
  }

  static boolean exists(RegistryKind kind, Id id) {
    Registry<? extends Keyed> registry = registry(kind);
    return registry != null && registry.get(BukkitConversions.key(id)) != null;
  }

  static Set<Id> ids(RegistryKind kind) {
    Registry<? extends Keyed> registry = registry(kind);
    if (registry == null) {
      return Set.of();
    }
    Set<Id> ids = new LinkedHashSet<>();
    collect(registry, ids);
    return ids;
  }

  private static <T extends Keyed> void collect(Registry<T> registry, Set<Id> ids) {
    registry.stream().forEach((T value) -> {
      NamespacedKey key = BukkitRegistries.key(registry, value);
      if (key != null) {
        ids.add(BukkitConversions.id(key));
      }
    });
  }

  static boolean isTagged(RegistryKind kind, Id tag, Id value) {
    return BukkitRegistries.isTagged(type(kind), tag, value);
  }

  static Set<Id> tagValues(RegistryKind kind, Id tag) {
    return BukkitRegistries.tagValues(type(kind), tag);
  }

  static String particleDataType(Id particle) {
    Particle resolved = particle == null ? null : Registry.PARTICLE_TYPE.get(BukkitConversions.key(particle));
    return resolved == null || resolved.getDataType() == Void.class ? null : resolved.getDataType().getSimpleName();
  }

  static EffectCategory effectCategory(Id effect) {
    PotionEffectType type = effect == null ? null : Registry.EFFECT.get(BukkitConversions.key(effect));
    if (type == null) {
      return null;
    }
    return switch (type.getCategory()) {
      case BENEFICIAL -> EffectCategory.BENEFICIAL;
      case HARMFUL -> EffectCategory.HARMFUL;
      case NEUTRAL -> EffectCategory.NEUTRAL;
    };
  }

  static boolean isInstantEffect(Id effect) {
    PotionEffectType type = effect == null ? null : Registry.EFFECT.get(BukkitConversions.key(effect));
    return type != null && type.isInstant();
  }

  static List<EffectInstance> potionEffects(Id potion) {
    PotionType type = potion == null ? null : Registry.POTION.get(BukkitConversions.key(potion));
    if (type == null) {
      return List.of();
    }
    List<EffectInstance> effects = new ArrayList<>();
    for (PotionEffect effect : type.getPotionEffects()) {
      effects.add(EffectInstance.of(BukkitResolve.id(effect.getType()), effect.isInfinite() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier()).withAmbient(effect.isAmbient()).withParticles(effect.hasParticles()).withIcon(effect.hasIcon()));
    }
    return effects;
  }

  static List<EffectModifier> effectModifiers(Id effect, int amplifier) {
    PotionEffectType type = effect == null ? null : Registry.EFFECT.get(BukkitConversions.key(effect));
    if (type == null) {
      return List.of();
    }
    return BukkitRegistries.effectModifiers(type, amplifier);
  }

  private static Registry<? extends Keyed> registry(RegistryKind kind) {
    Class<? extends Keyed> type = type(kind);
    return type == null ? null : BukkitRegistries.registry(type);
  }

  private static Class<? extends Keyed> type(RegistryKind kind) {
    return kind == null ? null : TYPES.get(kind);
  }

  private static Map<RegistryKind, Class<? extends Keyed>> types() {
    Map<RegistryKind, Class<? extends Keyed>> types = new EnumMap<>(RegistryKind.class);
    types.put(RegistryKind.ITEM, ItemType.class);
    types.put(RegistryKind.BLOCK, BlockType.class);
    types.put(RegistryKind.ENTITY_TYPE, EntityType.class);
    types.put(RegistryKind.SOUND, Sound.class);
    types.put(RegistryKind.PARTICLE, Particle.class);
    types.put(RegistryKind.EFFECT, PotionEffectType.class);
    types.put(RegistryKind.ENCHANTMENT, Enchantment.class);
    types.put(RegistryKind.ATTRIBUTE, Attribute.class);
    types.put(RegistryKind.BIOME, Biome.class);
    types.put(RegistryKind.DAMAGE_TYPE, DamageType.class);
    types.put(RegistryKind.POTION, PotionType.class);
    return types;
  }
}
