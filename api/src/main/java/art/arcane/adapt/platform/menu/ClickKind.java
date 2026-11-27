package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

/**
 * How the viewer clicked a menu slot.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum ClickKind {
  LEFT,
  RIGHT,
  SHIFT_LEFT,
  SHIFT_RIGHT,
  MIDDLE,
  /** A hotbar number key pressed while hovering the slot. */
  NUMBER_KEY,
  /** The drop key. */
  DROP,
  /** The drop key with control held. */
  CONTROL_DROP,
  DOUBLE_CLICK,
  /** The swap-offhand key. */
  SWAP_OFFHAND,
  /** Any other click, and the context of typed {@link MenuInput}. */
  OTHER;

  /** True for {@link #SHIFT_LEFT} and {@link #SHIFT_RIGHT}. */
  public boolean isShift() {
    return this == SHIFT_LEFT || this == SHIFT_RIGHT;
  }

  /** True for {@link #LEFT}, {@link #SHIFT_LEFT} and {@link #DOUBLE_CLICK}. */
  public boolean isLeft() {
    return this == LEFT || this == SHIFT_LEFT || this == DOUBLE_CLICK;
  }

  /** True for {@link #RIGHT} and {@link #SHIFT_RIGHT}. */
  public boolean isRight() {
    return this == RIGHT || this == SHIFT_RIGHT;
  }
}
