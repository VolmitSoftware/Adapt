package art.arcane.adapt.platform.item;

import org.jetbrains.annotations.ApiStatus;

/**
 * A kind of hand tool, as tags, loader tool actions and mining rules name it.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum ToolKind {
  PICKAXE,
  AXE,
  SHOVEL,
  HOE,
  SWORD
}
