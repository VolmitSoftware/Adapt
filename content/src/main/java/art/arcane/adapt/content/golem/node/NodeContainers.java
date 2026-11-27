package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Optional;

public final class NodeContainers {
  private static final String SHULKER_BOX = "shulker_box";

  private NodeContainers() {
  }

  public static Optional<InventoryRef> inventory(BlockRef block) {
    return Optional.ofNullable(block.container());
  }

  public static long key(BlockRef block, InventoryRef inventory) {
    List<BlockRef> halves = block.containerHalves();
    BlockRef left = halves.size() == 2 ? halves.getFirst() : block;
    return blockKey(left.x(), left.y(), left.z());
  }

  public static long blockKey(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }

  public static boolean full(InventoryRef inventory) {
    for (ItemRef slot : inventory.contents()) {
      if (slot == null || slot.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public static boolean isShulkerBox(Id material) {
    return material.path().endsWith(SHULKER_BOX);
  }

  public static boolean nests(Id container, ItemRef item) {
    return container != null && isShulkerBox(container) && isShulkerBox(item.type());
  }

  public static int room(InventoryRef inventory, ItemRef item) {
    int stackSize = Math.max(1, item.maxStackSize());
    int room = 0;
    for (ItemRef slot : inventory.contents()) {
      if (slot == null || slot.isEmpty()) {
        room += stackSize;
      } else if (slot.isSimilar(item)) {
        room += Math.max(0, stackSize - slot.amount());
      }
    }
    return room;
  }

  public static boolean fits(InventoryRef inventory, List<ItemRef> items) {
    for (ItemRef item : items) {
      if (room(inventory, item) > 0) {
        return true;
      }
    }
    return false;
  }

  public static boolean fits(Id container, InventoryRef inventory, List<ItemRef> items) {
    for (ItemRef item : items) {
      if (!nests(container, item) && room(inventory, item) > 0) {
        return true;
      }
    }
    return false;
  }
}
