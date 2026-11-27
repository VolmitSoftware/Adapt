package art.arcane.adapt.platform;

import org.jetbrains.annotations.ApiStatus;

/**
 * The game registries {@link RegistryLookup} and {@link ContentAliases} cover.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum RegistryKind {
  ITEM,
  BLOCK,
  ENTITY_TYPE,
  SOUND,
  PARTICLE,
  /** Mob effects such as {@code minecraft:speed}. */
  EFFECT,
  ENCHANTMENT,
  ATTRIBUTE,
  BIOME,
  DAMAGE_TYPE,
  /** Potion types such as {@code minecraft:swiftness}, not mob effects. */
  POTION
}
