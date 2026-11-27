package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Slot groups an item attribute modifier can be active in.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum EquipmentSlotGroup {
  /** Every slot. */
  ANY,
  MAINHAND,
  OFFHAND,
  /** Either hand. */
  HAND,
  FEET,
  LEGS,
  CHEST,
  HEAD,
  /** Any of the four armor slots. */
  ARMOR,
  /** Animal body armor slot. */
  BODY,
  SADDLE
}
