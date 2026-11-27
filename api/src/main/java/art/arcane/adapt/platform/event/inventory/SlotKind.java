package art.arcane.adapt.platform.event.inventory;

import org.jetbrains.annotations.ApiStatus;

/**
 * Role of a clicked slot, with Bukkit's slot type names.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum SlotKind {
  /** An output slot, such as a crafting or furnace result. */
  RESULT,
  /** A crafting grid input slot. */
  CRAFTING,
  /** A player armor slot. */
  ARMOR,
  /** Any other slot of a container or the player inventory. */
  CONTAINER,
  /** A hotbar slot. */
  QUICKBAR,
  /** A click outside the view. */
  OUTSIDE,
  /** A furnace fuel slot. */
  FUEL
}
