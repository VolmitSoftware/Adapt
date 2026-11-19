package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.value.Id;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry access through Paper's registry API. */
final class BukkitRegistryVersion {
  private static final Map<RegistryKind, RegistryKey<? extends Keyed>> KEYS = keys();

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
      NamespacedKey key = registry.getKey(value);
      if (key != null) {
        ids.add(BukkitConversions.id(key));
      }
    });
  }

  static boolean isTagged(RegistryKind kind, Id tag, Id value) {
    return isTagged(registryKey(kind), tag, value);
  }

  static Set<Id> tagValues(RegistryKind kind, Id tag) {
    return tagValues(registryKey(kind), tag);
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
      effects.add(new EffectInstance(BukkitResolve.id(effect.getType()),
          effect.isInfinite() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier(), effect.isAmbient(),
          effect.hasParticles(), effect.hasIcon()));
    }
    return effects;
  }

  static List<EffectModifier> effectModifiers(Id effect, int amplifier) {
    PotionEffectType type = effect == null ? null : Registry.EFFECT.get(BukkitConversions.key(effect));
    if (type == null) {
      return List.of();
    }
    List<EffectModifier> modifiers = new ArrayList<>();
    for (Map.Entry<Attribute, AttributeModifier> entry : type.getEffectAttributes().entrySet()) {
      modifiers.add(new EffectModifier(BukkitResolve.id(entry.getKey()), BukkitConversions.operation(entry.getValue().getOperation()),
          type.getAttributeModifierAmount(entry.getKey(), amplifier)));
    }
    return modifiers;
  }

  private static <T extends Keyed> boolean isTagged(RegistryKey<T> key, Id tag, Id value) {
    Tag<T> resolved = tag(key, tag);
    return resolved != null && value != null && resolved.contains(TypedKey.create(key, BukkitConversions.key(value)));
  }

  private static <T extends Keyed> Set<Id> tagValues(RegistryKey<T> key, Id tag) {
    Tag<T> resolved = tag(key, tag);
    if (resolved == null) {
      return Set.of();
    }
    Set<Id> ids = new LinkedHashSet<>();
    for (TypedKey<T> typed : resolved.values()) {
      ids.add(Id.of(typed.key().namespace(), typed.key().value()));
    }
    return Set.copyOf(ids);
  }

  private static <T extends Keyed> Tag<T> tag(RegistryKey<T> key, Id tag) {
    if (key == null || tag == null) {
      return null;
    }
    try {
      Registry<T> registry = RegistryAccess.registryAccess().getRegistry(key);
      TagKey<T> tagKey = TagKey.create(key, BukkitConversions.key(tag));
      return registry.hasTag(tagKey) ? registry.getTag(tagKey) : null;
    } catch (RuntimeException unavailable) {
      return null;
    }
  }

  private static Registry<? extends Keyed> registry(RegistryKind kind) {
    RegistryKey<? extends Keyed> key = registryKey(kind);
    if (key == null) {
      return null;
    }
    try {
      return RegistryAccess.registryAccess().getRegistry(key);
    } catch (RuntimeException unavailable) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Keyed> RegistryKey<T> registryKey(RegistryKind kind) {
    return kind == null ? null : (RegistryKey<T>) KEYS.get(kind);
  }

  private static Map<RegistryKind, RegistryKey<? extends Keyed>> keys() {
    Map<RegistryKind, RegistryKey<? extends Keyed>> keys = new EnumMap<>(RegistryKind.class);
    keys.put(RegistryKind.ITEM, RegistryKey.ITEM);
    keys.put(RegistryKind.BLOCK, RegistryKey.BLOCK);
    keys.put(RegistryKind.ENTITY_TYPE, RegistryKey.ENTITY_TYPE);
    keys.put(RegistryKind.SOUND, RegistryKey.SOUND_EVENT);
    keys.put(RegistryKind.PARTICLE, RegistryKey.PARTICLE_TYPE);
    keys.put(RegistryKind.EFFECT, RegistryKey.MOB_EFFECT);
    keys.put(RegistryKind.ENCHANTMENT, RegistryKey.ENCHANTMENT);
    keys.put(RegistryKind.ATTRIBUTE, RegistryKey.ATTRIBUTE);
    keys.put(RegistryKind.BIOME, RegistryKey.BIOME);
    keys.put(RegistryKind.DAMAGE_TYPE, RegistryKey.DAMAGE_TYPE);
    keys.put(RegistryKind.POTION, RegistryKey.POTION);
    return keys;
  }
}
