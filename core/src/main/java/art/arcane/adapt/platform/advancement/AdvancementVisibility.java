package art.arcane.adapt.platform.advancement;

/** When an ungranted advancement shows in the tab. Same rules as Adapt's current visibility constants. */
public enum AdvancementVisibility {
  /** Shown once the parent or grandparent is granted. */
  VANILLA,
  ALWAYS,
  /** Shown once the parent is granted. */
  PARENT_GRANTED,
  /** Shown only once granted. */
  HIDDEN
}
