package art.arcane.adapt.api.player;

import org.jetbrains.annotations.ApiStatus;

/**
 * The kinds of first finds a player's profile keeps, each a set of keys the player has seen.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum DiscoveryKind {
  /** A block type, by its block data string. */
  BLOCK,
  /** An item type. */
  ITEM,
  /** A crafting recipe, by its key. */
  RECIPE,
  /** A food eaten. */
  FOOD,
  /** A creature type. */
  MOB,
  /** Another player, by uuid. */
  PERSON,
  /** An enchantment with its level. */
  ENCHANTMENT,
  /** A world, by key and seed. */
  WORLD,
  /** A world environment, by world name. */
  ENVIRONMENT,
  /** A potion effect with its amplifier. */
  POTION_EFFECT,
  /** A biome. */
  BIOME
}
