package art.arcane.adapt.platform.item;

import org.jetbrains.annotations.ApiStatus;

/**
 * A tooltip section an item can hide (Bukkit item flags, vanilla tooltip_display hidden components).
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum TooltipPart {
  ATTRIBUTES,
  ENCHANTMENTS,
  POTION_EFFECTS,
  UNBREAKABLE,
  /** Item-specific detail lines (firework, container, sherd and similar contents; Bukkit HIDE_ADDITIONAL_TOOLTIP). */
  ADDITIONAL,
  /** Dyed colour line (Bukkit HIDE_DYE). */
  DYE
}
