package art.arcane.adapt.platform.item;

/** A tooltip section an item can hide (Bukkit item flags, vanilla tooltip_display hidden components). */
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
