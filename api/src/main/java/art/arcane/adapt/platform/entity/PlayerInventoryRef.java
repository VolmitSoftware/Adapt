package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

/** Player inventory. Slot numbering follows Bukkit: 0-8 hotbar, 9-35 main, 36-39 armor, 40 off hand. */
@ApiStatus.AvailableSince("1.0.0")
public interface PlayerInventoryRef extends InventoryRef {
  /** Stack in the selected hotbar slot. */
  ItemRef mainHand();

  /** Replaces the stack in the selected hotbar slot; null or empty clears it. */
  void setMainHand(ItemRef item);

  /** Stack in the off hand. */
  ItemRef offHand();

  /** Replaces the off-hand stack; null or empty clears it. */
  void setOffHand(ItemRef item);

  /** Stack in the given hand. */
  default ItemRef hand(Hand hand) {
    return hand == Hand.MAIN_HAND ? mainHand() : offHand();
  }

  /** Replaces the stack in the given hand. */
  default void setHand(Hand hand, ItemRef item) {
    if (hand == Hand.MAIN_HAND) {
      setMainHand(item);
    } else {
      setOffHand(item);
    }
  }

  /** Stack worn in the equipment slot. */
  ItemRef armor(EquipmentSlot slot);

  /** Replaces the stack worn in the equipment slot; null or empty clears it. */
  void setArmor(EquipmentSlot slot, ItemRef item);

  /** Selected hotbar slot, 0 to 8. */
  int heldSlot();

  /** Selects a hotbar slot, 0 to 8. */
  void setHeldSlot(int slot);

  /** Stack the player carries on the cursor in an open window; empty when none. */
  ItemRef cursor();

  /** Replaces the cursor stack; null or empty clears it. */
  void setCursor(ItemRef item);
}
