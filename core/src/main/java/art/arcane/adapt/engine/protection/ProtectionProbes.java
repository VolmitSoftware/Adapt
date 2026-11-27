package art.arcane.adapt.engine.protection;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Break and pickup attempts that run through every protection plugin, with the online, same-world and region
 * ownership checks Adapt applies before it acts for a player.
 */

public final class ProtectionProbes {
  private static final int PLAYER_STORAGE_SLOTS = 36;
  private static final Id CHEST = Id.minecraft("chest");
  private static final Id TRAPPED_CHEST = Id.minecraft("trapped_chest");

  private ProtectionProbes() {
  }

  /** Breaks the block as the player, through the platform break event. True when it broke. */
  public static boolean attemptBlockBreak(PlayerRef player, BlockRef block) {
    if (!canAttemptBlockAction(player, block) || !sameWorld(player, block)) {
      return false;
    }
    return AdaptCore.platform().protection().attemptBreak(player, block);
  }

  /** True when no protection plugin vetoes the player breaking the block; the block stays. */
  public static boolean attemptBlockBreakProbe(PlayerRef player, BlockRef block) {
    if (!canAttemptBlockAction(player, block) || !sameWorld(player, block)) {
      return false;
    }
    return port().probeBreak(player, block);
  }

  /** Fires an ordinary break event every listener sees; true when nobody cancelled it. The block stays. */
  public static boolean callBlockBreak(PlayerRef player, BlockRef block) {
    return port().callBreak(player, block);
  }

  /** True when no protection plugin vetoes the player using (right-clicking) the block. */
  public static boolean attemptBlockUse(PlayerRef player, BlockRef block) {
    if (!canAttemptBlockAction(player, block)) {
      return false;
    }
    return port().probeUse(player, block);
  }

  /** True when no protection plugin vetoes the player placing at the block; nothing is placed. */
  public static boolean attemptBlockPlaceProbe(PlayerRef player, BlockRef block) {
    if (!canAttemptBlockAction(player, block) || !sameWorld(player, block)) {
      return false;
    }
    return port().probePlace(player, block);
  }

  /**
   * After the caller set {@code block}, tells every protection and logging plugin that the player placed it with
   * {@code item}; Adapt's own skills ignore it. True when allowed; on false restore {@code replaced}.
   */
  public static boolean callPlace(PlayerRef player, BlockRef block, BlockStateRef replaced, BlockRef against, ItemRef item) {
    if (!canAttemptBlockAction(player, block) || !sameWorld(player, block)) {
      return false;
    }
    return port().callPlace(player, block, replaced, against, item);
  }

  /**
   * Tells every protection and logging plugin that the player is removing {@code block}; Adapt's own skills ignore
   * it. True when allowed; the caller then removes the block without drops.
   */
  public static boolean callRemove(PlayerRef player, BlockRef block) {
    if (!canAttemptBlockAction(player, block) || !sameWorld(player, block)) {
      return false;
    }
    return port().callRemove(player, block);
  }

  /** True when no protection plugin vetoes the player growing the tree feature into exactly these blocks. */
  public static boolean attemptTreeGrow(PlayerRef player, BlockRef origin, Id feature, List<BlockChange> blocks) {
    return port().probeTreeGrow(player, origin, feature, blocks);
  }

  /** True when no protection plugin vetoes the player taking a block drop that has not spawned yet. */
  public static boolean attemptBlockDropPickup(PlayerRef player, ItemEntityRef item, int remaining, Loc source) {
    if (Tasks.isFoliaThreading() && (!Tasks.isOwnedByCurrentRegion(player) || source == null
        || !Tasks.isOwnedByCurrentRegion(source))) {
      return false;
    }
    if (!player.isOnline() || item.isDead() || source == null
        || !player.world().uid().equals(item.world().uid())
        || !source.world().uid().equals(item.world().uid())) {
      return false;
    }
    ItemRef stack = item.item();
    if (stack == null || stack.isEmpty() || stack.amount() <= 0) {
      return false;
    }
    int normalizedRemaining = Math.min(stack.amount(), Math.max(0, remaining));
    if (normalizedRemaining >= stack.amount()) {
      return false;
    }
    return port().probeDropPickup(player, item, normalizedRemaining, source);
  }

  /** True when no protection plugin vetoes the player picking up a live item entity, leaving {@code remaining}. */
  public static boolean attemptItemPickup(PlayerRef player, ItemEntityRef item, int remaining) {
    if (Tasks.isFoliaThreading() && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(item))) {
      return false;
    }
    if (!player.isOnline() || item.isDead() || !item.isValid()
        || !player.world().uid().equals(item.world().uid())) {
      return false;
    }
    ItemRef stack = item.item();
    if (stack == null || stack.isEmpty() || stack.amount() <= 0) {
      return false;
    }
    int normalizedRemaining = Math.min(stack.amount(), Math.max(0, remaining));
    if (normalizedRemaining >= stack.amount()) {
      return false;
    }
    return port().probeItemPickup(player, item, normalizedRemaining);
  }

  /** True when no protection plugin vetoes the non-player {@code entity} picking up the live {@code item}. */
  public static boolean attemptEntityItemPickup(LivingRef entity, ItemEntityRef item, int remaining) {
    if (!Tasks.isOwnedByCurrentRegion(item) || !item.isValid() || item.isDead()
        || !entity.world().uid().equals(item.world().uid())) {
      return false;
    }
    ItemRef stack = item.item();
    if (stack == null || stack.isEmpty() || stack.amount() <= 0) {
      return false;
    }
    int normalizedRemaining = Math.min(stack.amount(), Math.max(0, remaining));
    if (normalizedRemaining >= stack.amount()) {
      return false;
    }
    return port().probeEntityPickup(entity, item, normalizedRemaining);
  }

  /** Items of {@code stack} that would not fit into the player's storage slots. */
  public static int remainingAfterPickup(PlayerInventoryRef inventory, ItemRef stack) {
    int remaining = stack.amount();
    int maxStackSize = stack.maxStackSize();
    int slots = Math.min(PLAYER_STORAGE_SLOTS, inventory.size());
    for (int slot = 0; slot < slots; slot++) {
      ItemRef current = inventory.item(slot);
      if (current == null || current.isEmpty()) {
        remaining -= maxStackSize;
      } else if (current.isSimilar(stack)) {
        remaining -= Math.max(0, maxStackSize - current.amount());
      }
      if (remaining <= 0) {
        return 0;
      }
    }
    return remaining;
  }

  /** Items of {@code stack} that would not fit into the storage slots of {@code inventory}. */
  public static int remainingAfterPickup(InventoryRef inventory, ItemRef stack) {
    if (inventory instanceof PlayerInventoryRef playerInventory) {
      return remainingAfterPickup(playerInventory, stack);
    }
    int remaining = stack.amount();
    int maxStackSize = Math.min(stack.maxStackSize(), inventory.maxStackSize());
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef current = inventory.item(slot);
      if (current == null || current.isEmpty()) {
        remaining -= maxStackSize;
      } else if (current.isSimilar(stack)) {
        remaining -= Math.max(0, maxStackSize - current.amount());
      }
      if (remaining <= 0) {
        return 0;
      }
    }
    return remaining;
  }

  /**
   * The physical blocks of the container at {@code target}: the target, the other half a connected chest block
   * points at and, with {@code includeInventoryHalves}, both halves of the double chest inventory it opens.
   */
  public static List<BlockRef> containerBlocks(BlockRef target, boolean includeInventoryHalves) {
    Set<BlockRef> blocks = new LinkedHashSet<>();
    blocks.add(target);
    BlockRef connected = connectedChest(target);
    if (connected != null) {
      blocks.add(connected);
    }
    if (includeInventoryHalves) {
      blocks.addAll(target.containerHalves());
    }
    return List.copyOf(blocks);
  }

  /** True when the player may use every one of the container blocks, probed in order. */
  public static boolean attemptContainerOpen(PlayerRef player, List<BlockRef> blocks) {
    for (BlockRef block : blocks) {
      if (!attemptBlockUse(player, block)) {
        return false;
      }
    }
    return true;
  }

  private static BlockRef connectedChest(BlockRef target) {
    BlockStateRef state = target.state();
    String type = state.property("type");
    String facing = state.property("facing");
    if (type == null || facing == null || type.equals("single")
        || (!target.type().equals(CHEST) && !target.type().equals(TRAPPED_CHEST))) {
      return null;
    }
    BlockFace face = switch (facing) {
      case "north" -> BlockFace.NORTH;
      case "east" -> BlockFace.EAST;
      case "south" -> BlockFace.SOUTH;
      case "west" -> BlockFace.WEST;
      default -> BlockFace.SELF;
    };
    return target.relative(type.equals("left") ? clockwise(face) : counterClockwise(face));
  }

  private static BlockFace clockwise(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockFace.EAST;
      case EAST -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.WEST;
      case WEST -> BlockFace.NORTH;
      default -> BlockFace.SELF;
    };
  }

  private static BlockFace counterClockwise(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockFace.WEST;
      case WEST -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.EAST;
      case EAST -> BlockFace.NORTH;
      default -> BlockFace.SELF;
    };
  }

  private static boolean canAttemptBlockAction(PlayerRef player, BlockRef block) {
    if (Tasks.isFoliaThreading()) {
      if (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(block.location())) {
        return false;
      }
      return player.isOnline() && sameWorld(player, block);
    }
    return player.isOnline();
  }

  private static boolean sameWorld(PlayerRef player, BlockRef block) {
    return player.world().uid().equals(block.world().uid());
  }

  private static ProtectionProbePort port() {
    return AdaptPorts.get(ProtectionProbePort.class, ProtectionProbePort.NONE);
  }
}
