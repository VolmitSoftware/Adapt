package art.arcane.adapt.platform.entity;

public enum Hand {
  MAIN_HAND,
  OFF_HAND;

  public EquipmentSlot slot() {
    return this == MAIN_HAND ? EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND;
  }
}
