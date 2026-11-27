package art.arcane.adapt.bukkit.platform;

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
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry access through Paper's registry API. Loaded only on Paper; {@link BukkitRegistries} picks it. */
final class PaperRegistries {
  private static final Map<Class<? extends Keyed>, RegistryKey<? extends Keyed>> KEYS = Map.ofEntries(
      Map.entry(ItemType.class, RegistryKey.ITEM),
      Map.entry(BlockType.class, RegistryKey.BLOCK),
      Map.entry(EntityType.class, RegistryKey.ENTITY_TYPE),
      Map.entry(Sound.class, RegistryKey.SOUND_EVENT),
      Map.entry(Particle.class, RegistryKey.PARTICLE_TYPE),
      Map.entry(PotionEffectType.class, RegistryKey.MOB_EFFECT),
      Map.entry(Enchantment.class, RegistryKey.ENCHANTMENT),
      Map.entry(Attribute.class, RegistryKey.ATTRIBUTE),
      Map.entry(Biome.class, RegistryKey.BIOME),
      Map.entry(DamageType.class, RegistryKey.DAMAGE_TYPE),
      Map.entry(PotionType.class, RegistryKey.POTION),
      Map.entry(PatternType.class, RegistryKey.BANNER_PATTERN),
      Map.entry(Structure.class, RegistryKey.STRUCTURE),
      Map.entry(StructureType.class, RegistryKey.STRUCTURE_TYPE));

  private PaperRegistries() {
  }

  static <T extends Keyed> Registry<T> registry(Class<T> type) {
    return RegistryAccess.registryAccess().getRegistry(key(type));
  }

  static <T extends Keyed> NamespacedKey key(Registry<T> registry, T value) {
    return registry.getKey(value);
  }

  static <T extends Keyed> NamespacedKey keyOrThrow(Class<T> type, T value) {
    return registry(type).getKeyOrThrow(value);
  }

  static <T extends Keyed> boolean isTagged(Class<T> type, Id tag, Id value) {
    Tag<T> resolved = tag(type, tag);
    return resolved != null && value != null && resolved.contains(TypedKey.create(key(type), BukkitConversions.key(value)));
  }

  static <T extends Keyed> Set<Id> tagValues(Class<T> type, Id tag) {
    Tag<T> resolved = tag(type, tag);
    if (resolved == null) {
      return Set.of();
    }
    Set<Id> ids = new LinkedHashSet<>();
    for (TypedKey<T> typed : resolved.values()) {
      ids.add(Id.of(typed.key().namespace(), typed.key().value()));
    }
    return Set.copyOf(ids);
  }

  static List<EffectModifier> effectModifiers(PotionEffectType type, int amplifier) {
    List<EffectModifier> modifiers = new ArrayList<>();
    for (Map.Entry<Attribute, AttributeModifier> entry : type.getEffectAttributes().entrySet()) {
      modifiers.add(new EffectModifier(entry.getKey().getTranslationKey(), BukkitConversions.operation(entry.getValue().getOperation()),
          type.getAttributeModifierAmount(entry.getKey(), amplifier)));
    }
    return modifiers;
  }

  static boolean hasDataComponents() {
    return RegistryAccess.registryAccess().getRegistry(RegistryKey.DATA_COMPONENT_TYPE).iterator().hasNext();
  }

  private static <T extends Keyed> Tag<T> tag(Class<T> type, Id tag) {
    if (tag == null) {
      return null;
    }
    try {
      Registry<T> registry = registry(type);
      TagKey<T> tagKey = TagKey.create(key(type), BukkitConversions.key(tag));
      return registry.hasTag(tagKey) ? registry.getTag(tagKey) : null;
    } catch (RuntimeException unavailable) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Keyed> RegistryKey<T> key(Class<T> type) {
    RegistryKey<T> key = (RegistryKey<T>) KEYS.get(type);
    if (key == null) {
      throw new IllegalArgumentException("No Paper registry for " + type.getName());
    }
    return key;
  }
}
