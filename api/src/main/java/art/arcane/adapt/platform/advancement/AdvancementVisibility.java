package art.arcane.adapt.platform.advancement;

import org.jetbrains.annotations.ApiStatus;

/**
 * When an ungranted advancement shows in the tab. Same rules as Adapt's current visibility constants.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AdvancementVisibility {
  /** Shown once the parent or grandparent is granted. */
  VANILLA,
  /** Always shown. */
  ALWAYS,
  /** Shown once the parent is granted. */
  PARENT_GRANTED,
  /** Shown only once granted. */
  HIDDEN
}
