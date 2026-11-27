package art.arcane.adapt.platform.event.player;

import org.jetbrains.annotations.ApiStatus;

/**
 * Kind of click a {@link PlayerInteractEvent} reports.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum InteractAction {
  LEFT_CLICK_BLOCK,
  RIGHT_CLICK_BLOCK,
  LEFT_CLICK_AIR,
  RIGHT_CLICK_AIR,
  /** Stepping on or into a block that reacts to it, such as a pressure plate, tripwire or farmland. */
  PHYSICAL;

  /** True for a right click on a block or on air. */
  public boolean isRightClick() {
    return this == RIGHT_CLICK_BLOCK || this == RIGHT_CLICK_AIR;
  }

  /** True for a left click on a block or on air. */
  public boolean isLeftClick() {
    return this == LEFT_CLICK_BLOCK || this == LEFT_CLICK_AIR;
  }
}
