package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Equipment slot of a living entity.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum EquipmentSlot {
  MAIN_HAND,
  OFF_HAND,
  HEAD,
  CHEST,
  LEGS,
  FEET,
  /** Body armor of animals such as horse armor and wolf armor. */
  BODY,
  /** Saddle of a rideable animal. */
  SADDLE;

  /** True for the four player armor slots: head, chest, legs and feet. */
  public boolean isArmor() {
    return this == HEAD || this == CHEST || this == LEGS || this == FEET;
  }
}
