package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.PlayerInventory;

public final class BukkitPlayerInventoryRef extends BukkitInventoryRef implements PlayerInventoryRef {
  private final PlayerInventory player;

  public BukkitPlayerInventoryRef(PlayerInventory inventory) {
    super(inventory);
    this.player = inventory;
  }

  @Override
  public ItemRef mainHand() {
    return BukkitRefs.item(player.getItemInMainHand());
  }

  @Override
  public void setMainHand(ItemRef item) {
    player.setItemInMainHand(stackOrNull(item));
  }

  @Override
  public ItemRef offHand() {
    return BukkitRefs.item(player.getItemInOffHand());
  }

  @Override
  public void setOffHand(ItemRef item) {
    player.setItemInOffHand(stackOrNull(item));
  }

  @Override
  public ItemRef armor(EquipmentSlot slot) {
    return BukkitRefs.item(player.getItem(BukkitConversions.slot(slot)));
  }

  @Override
  public void setArmor(EquipmentSlot slot, ItemRef item) {
    player.setItem(BukkitConversions.slot(slot), stackOrNull(item));
  }

  @Override
  public int heldSlot() {
    return player.getHeldItemSlot();
  }

  @Override
  public void setHeldSlot(int slot) {
    player.setHeldItemSlot(slot);
  }

  @Override
  public ItemRef cursor() {
    HumanEntity holder = player.getHolder();
    return BukkitRefs.item(holder == null ? null : holder.getItemOnCursor());
  }

  @Override
  public void setCursor(ItemRef item) {
    HumanEntity holder = player.getHolder();
    if (holder != null) {
      holder.setItemOnCursor(stackOrNull(item));
    }
  }
}
