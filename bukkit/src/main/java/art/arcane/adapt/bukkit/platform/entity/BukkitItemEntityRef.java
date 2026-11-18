package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class BukkitItemEntityRef extends BukkitEntityRef implements ItemEntityRef {
  public BukkitItemEntityRef(Item item) {
    super(item);
  }

  public Item itemEntity() {
    return (Item) handle();
  }

  @Override
  public ItemRef item() {
    return BukkitRefs.item(itemEntity().getItemStack());
  }

  @Override
  public void setItem(ItemRef item) {
    ItemStack stack = BukkitInventoryRef.stackOrNull(item);
    itemEntity().setItemStack(stack == null ? new ItemStack(Material.AIR) : stack);
  }

  @Override
  public int pickupDelay() {
    return itemEntity().getPickupDelay();
  }

  @Override
  public void setPickupDelay(int ticks) {
    itemEntity().setPickupDelay(ticks);
  }

  @Override
  public UUID thrower() {
    return itemEntity().getThrower();
  }

  @Override
  public void setThrower(UUID thrower) {
    itemEntity().setThrower(thrower);
  }

  @Override
  public UUID owner() {
    return itemEntity().getOwner();
  }

  @Override
  public void setOwner(UUID owner) {
    itemEntity().setOwner(owner);
  }

  @Override
  public boolean canPlayerPickup() {
    return PaperCompat.canPlayerPickup(itemEntity());
  }

  @Override
  public boolean canMobPickup() {
    return PaperCompat.canMobPickup(itemEntity());
  }

  @Override
  public void setCanMobPickup(boolean pickup) {
    PaperCompat.setCanMobPickup(itemEntity(), pickup);
  }

  @Override
  public void setUnlimitedLifetime(boolean unlimited) {
    itemEntity().setUnlimitedLifetime(unlimited);
  }
}
