package art.arcane.adapt.platform;

import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Set;

/**
 * Read-only view of the game registries. Safe on any thread after server start. Content checks
 * {@link #exists} before using ids that are new in later Minecraft versions. Ids are always the 26.x names: an
 * adapter for an older version translates renamed ids through {@link ContentAliases} in both directions.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface RegistryLookup {
  /** True when the id is registered and usable: an entry disabled by a feature flag does not exist. */
  boolean exists(RegistryKind kind, Id id);

  /** Every registered id of the kind. */
  Set<Id> ids(RegistryKind kind);

  /** True when {@code value} is in the registry tag {@code tag}, for example {@code minecraft:logs}. */
  boolean isTagged(RegistryKind kind, Id tag, Id value);

  /** Every id in the registry tag. */
  Set<Id> tagValues(RegistryKind kind, Id tag);

  /** Vanilla max level of an enchantment, or 0 when unknown. */
  int enchantmentMaxLevel(Id enchantment);

  /** Simple name of the data type a particle needs to be spawned, or null when it needs none or is unknown. */
  String particleDataType(Id particle);

  /** Category of a mob effect, or null when the effect is unknown. */
  EffectCategory effectCategory(Id effect);

  /** True for instant mob effects (instant health, instant damage, saturation). */
  boolean isInstantEffect(Id effect);

  /** Effects of a potion type (minecraft:swiftness ...) in registry order; empty when the potion is unknown. */
  List<EffectInstance> potionEffects(Id potion);

  /** Attribute modifiers a mob effect applies at the amplifier, in registry order; empty when it applies none. */
  List<EffectModifier> effectModifiers(Id effect, int amplifier);
}
