package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public final class NodeContainers {
  private NodeContainers() {
  }

  public static Optional<Inventory> inventory(Block block) {
    return PaperCompat.liveState(block) instanceof Container container ? Optional.of(container.getInventory()) : Optional.empty();
  }

  public static long key(Block block, Inventory inventory) {
    if (inventory instanceof DoubleChestInventory chest) {
      Location left = chest.getLeftSide().getLocation();
      if (left != null) {
        return blockKey(left.getBlockX(), left.getBlockY(), left.getBlockZ());
      }
    }
    return blockKey(block.getX(), block.getY(), block.getZ());
  }

  public static long blockKey(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }

  public static boolean full(Inventory inventory) {
    return inventory.firstEmpty() < 0;
  }

  public static int room(Inventory inventory, ItemStack item) {
    int stackSize = Math.max(1, Math.min(item.getMaxStackSize(), inventory.getMaxStackSize()));
    int room = 0;
    for (ItemStack slot : inventory.getStorageContents()) {
      if (slot == null || slot.getType() == Material.AIR) {
        room += stackSize;
      } else if (slot.isSimilar(item)) {
        room += Math.max(0, stackSize - slot.getAmount());
      }
    }
    return room;
  }

  public static boolean fits(Inventory inventory, List<ItemStack> items) {
    for (ItemStack item : items) {
      if (room(inventory, item) > 0) {
        return true;
      }
    }
    return false;
  }
}
