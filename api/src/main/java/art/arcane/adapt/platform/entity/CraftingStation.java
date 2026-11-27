package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Utility block windows a player can open without the block.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum CraftingStation {
  WORKBENCH,
  GRINDSTONE,
  ANVIL,
  STONECUTTER,
  CARTOGRAPHY,
  LOOM
}
