package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;

/** Player inventory. Slot numbering follows Bukkit: 0-8 hotbar, 9-35 main, 36-39 armor, 40 off hand. */
public interface PlayerInventoryRef extends InventoryRef {
  ItemRef mainHand();

  void setMainHand(ItemRef item);

  ItemRef offHand();

  void setOffHand(ItemRef item);

  default ItemRef hand(Hand hand) {
    return hand == Hand.MAIN_HAND ? mainHand() : offHand();
  }

  default void setHand(Hand hand, ItemRef item) {
    if (hand == Hand.MAIN_HAND) {
      setMainHand(item);
    } else {
      setOffHand(item);
    }
  }

  ItemRef armor(EquipmentSlot slot);

  void setArmor(EquipmentSlot slot, ItemRef item);

  int heldSlot();

  void setHeldSlot(int slot);

  ItemRef cursor();

  void setCursor(ItemRef item);
}
