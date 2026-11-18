package art.arcane.adapt.platform.menu;

public enum ClickKind {
  LEFT,
  RIGHT,
  SHIFT_LEFT,
  SHIFT_RIGHT,
  MIDDLE,
  NUMBER_KEY,
  DROP,
  CONTROL_DROP,
  DOUBLE_CLICK,
  SWAP_OFFHAND,
  OTHER;

  public boolean isShift() {
    return this == SHIFT_LEFT || this == SHIFT_RIGHT;
  }

  public boolean isLeft() {
    return this == LEFT || this == SHIFT_LEFT || this == DOUBLE_CLICK;
  }

  public boolean isRight() {
    return this == RIGHT || this == SHIFT_RIGHT;
  }
}
