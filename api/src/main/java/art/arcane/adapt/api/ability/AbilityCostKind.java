package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * What an ability takes from the player by default, so a cost provider can decide whether to replace it.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AbilityCostKind {
  /** Items from the inventory; {@link AbilityCostContext#defaultItem()} holds one unit. */
  ITEM,
  /** Food points. */
  HUNGER,
  /** Health points. */
  HEALTH,
  /** Durability of the item in use. */
  DURABILITY,
  /** Vanilla experience, not Adapt XP. */
  VANILLA_EXPERIENCE
}
