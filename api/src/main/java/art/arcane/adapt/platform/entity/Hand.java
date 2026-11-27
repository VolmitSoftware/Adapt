package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/** One of a player's or mob's two hands. */
@ApiStatus.AvailableSince("1.0.0")
public enum Hand {
  MAIN_HAND,
  OFF_HAND;

  /** Equipment slot that holds this hand's item. */
  public EquipmentSlot slot() {
    return this == MAIN_HAND ? EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND;
  }
}
