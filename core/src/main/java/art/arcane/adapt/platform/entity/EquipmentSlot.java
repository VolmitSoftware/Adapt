package art.arcane.adapt.platform.entity;

public enum EquipmentSlot {
  MAIN_HAND,
  OFF_HAND,
  HEAD,
  CHEST,
  LEGS,
  FEET,
  BODY,
  SADDLE;

  public boolean isArmor() {
    return this == HEAD || this == CHEST || this == LEGS || this == FEET;
  }
}
