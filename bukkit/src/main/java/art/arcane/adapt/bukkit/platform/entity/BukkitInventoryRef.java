package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Container slots over a Bukkit inventory (double chests included: Bukkit merges both halves). */
public class BukkitInventoryRef implements InventoryRef {
  protected final Inventory inventory;

  public BukkitInventoryRef(Inventory inventory) {
    this.inventory = Objects.requireNonNull(inventory, "inventory");
  }

  public Inventory inventory() {
    return inventory;
  }

  @Override
  public int size() {
    return inventory.getSize();
  }

  @Override
  public int maxStackSize() {
    return inventory.getMaxStackSize();
  }

  @Override
  public ItemRef item(int slot) {
    return BukkitRefs.item(inventory.getItem(slot));
  }

  @Override
  public void setItem(int slot, ItemRef item) {
    inventory.setItem(slot, stackOrNull(item));
  }

  @Override
  public List<ItemRef> addItem(ItemRef item) {
    ItemStack stack = stackOrNull(item);
    if (stack == null) {
      return List.of();
    }
    HashMap<Integer, ItemStack> leftover = inventory.addItem(stack);
    if (leftover.isEmpty()) {
      return List.of();
    }
    List<ItemRef> rest = new ArrayList<>(leftover.size());
    for (ItemStack remaining : leftover.values()) {
      rest.add(BukkitRefs.item(remaining));
    }
    return rest;
  }

  @Override
  public int removeItem(Id type, int amount) {
    Material material = BukkitResolve.material(type);
    if (material == null || amount <= 0) {
      return 0;
    }
    int remaining = amount;
    ItemStack[] contents = inventory.getContents();
    for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
      ItemStack stack = contents[slot];
      if (stack == null || stack.getType() != material) {
        continue;
      }
      int take = Math.min(remaining, stack.getAmount());
      remaining -= take;
      if (take >= stack.getAmount()) {
        inventory.setItem(slot, null);
      } else {
        stack.setAmount(stack.getAmount() - take);
        inventory.setItem(slot, stack);
      }
    }
    return amount - remaining;
  }

  @Override
  public boolean containsAtLeast(ItemRef item, int amount) {
    ItemStack stack = stackOrNull(item);
    return stack != null && inventory.containsAtLeast(stack, amount);
  }

  @Override
  public int removeSimilar(ItemRef item, int amount) {
    ItemStack stack = stackOrNull(item);
    if (stack == null || amount <= 0) {
      return 0;
    }
    ItemStack request = stack.clone();
    request.setAmount(amount);
    int left = 0;
    for (ItemStack rest : inventory.removeItem(request).values()) {
      left += rest.getAmount();
    }
    return amount - left;
  }

  @Override
  public boolean contains(Id type, int amount) {
    Material material = BukkitResolve.material(type);
    return material != null && inventory.contains(material, Math.max(1, amount));
  }

  @Override
  public int first(Id type) {
    Material material = BukkitResolve.material(type);
    return material == null ? -1 : inventory.first(material);
  }

  @Override
  public List<ItemRef> contents() {
    ItemStack[] contents = inventory.getContents();
    List<ItemRef> copies = new ArrayList<>(contents.length);
    for (ItemStack stack : contents) {
      copies.add(BukkitRefs.item(stack == null ? null : stack.clone()));
    }
    return copies;
  }

  @Override
  public void clear(int slot) {
    inventory.setItem(slot, null);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BukkitInventoryRef that && inventory.equals(that.inventory);
  }

  @Override
  public int hashCode() {
    return inventory.hashCode();
  }

  protected static ItemStack stackOrNull(ItemRef item) {
    if (item == null || item.isEmpty()) {
      return null;
    }
    return BukkitRefs.item(item);
  }
}
