package art.arcane.adapt.platform.event.inventory;

import org.jetbrains.annotations.ApiStatus;

/**
 * Kind of an inventory, or of the top inventory of an open view, with Bukkit's type names.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum InventoryKind {
  /** The player's own inventory, the bottom part of every view. */
  PLAYER,
  /** The top of the player's own inventory view, with its 2x2 crafting grid. */
  CRAFTING,
  /** A crafting table. */
  WORKBENCH,
  CHEST,
  ENDER_CHEST,
  BARREL,
  SHULKER_BOX,
  HOPPER,
  /** A dispenser or dropper. */
  DISPENSER,
  FURNACE,
  SMOKER,
  BLAST_FURNACE,
  BREWING,
  ENCHANTING,
  ANVIL,
  GRINDSTONE,
  SMITHING,
  STONECUTTER,
  LOOM,
  CARTOGRAPHY,
  MERCHANT,
  BEACON,
  /** Any inventory without its own constant. */
  OTHER
}
