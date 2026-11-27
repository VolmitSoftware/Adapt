package art.arcane.adapt.platform.event.inventory;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a click will do, with Bukkit's names.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum InventoryAction {
  /** The click does nothing. */
  NOTHING,
  PICKUP_ALL,
  PICKUP_SOME,
  PICKUP_HALF,
  PICKUP_ONE,
  PLACE_ALL,
  PLACE_SOME,
  PLACE_ONE,
  SWAP_WITH_CURSOR,
  DROP_ALL_CURSOR,
  DROP_ONE_CURSOR,
  DROP_ALL_SLOT,
  DROP_ONE_SLOT,
  /** Shift-click: moves the stack to the other inventory of the view. */
  MOVE_TO_OTHER_INVENTORY,
  /** Number key: swaps the slot with a hotbar slot. */
  HOTBAR_SWAP,
  /** Creative middle-click: copies the stack to the cursor. */
  CLONE_STACK,
  /** Double-click: gathers matching items onto the cursor. */
  COLLECT_TO_CURSOR,
  /** An action the platform could not map. */
  UNKNOWN
}
