/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.util.common.plugin;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionEventProbe {
  private static final String ATTEMPT_PICKUP_EVENT = "org.bukkit.event.player.PlayerAttemptPickupItemEvent";
  private static final Constructor<? extends Event> ATTEMPT_PICKUP_CONSTRUCTOR =
      resolveAttemptPickupConstructor();
  private static final Set<Event> ACTIVE_PROBES = ConcurrentHashMap.newKeySet();

  private ProtectionEventProbe() {
  }

  public static void dispatch(Event event) {
    boolean owner = ACTIVE_PROBES.add(event);
    try {
      Bukkit.getPluginManager().callEvent(event);
    } finally {
      if (owner) {
        ACTIVE_PROBES.remove(event);
      }
    }
  }

  public static boolean isActive(Event event) {
    return ACTIVE_PROBES.contains(event);
  }

  public static List<Block> containerBlocks(Block target, Inventory inventory) {
    Set<Block> blocks = new LinkedHashSet<>();
    blocks.add(target);
    addConnectedChest(blocks, target);
    if (inventory != null && inventory.getHolder() instanceof DoubleChest doubleChest) {
      addContainerBlock(blocks, doubleChest.getLeftSide());
      addContainerBlock(blocks, doubleChest.getRightSide());
    }
    return List.copyOf(blocks);
  }

  public static boolean attemptContainerOpen(Player player, List<Block> blocks) {
    for (Block block : blocks) {
      if (!attemptBlockUse(player, block)) {
        return false;
      }
    }
    return true;
  }

  public static boolean attemptBlockUse(Player player, Block block) {
    return attemptBlockUse(player, block, EquipmentSlot.HAND);
  }

  public static boolean attemptBlockUse(Player player, Block block, EquipmentSlot hand) {
    if (!canAttemptBlockAction(player, block)) {
      return false;
    }
    ItemStack item = hand == EquipmentSlot.OFF_HAND
        ? player.getInventory().getItemInOffHand()
        : player.getInventory().getItemInMainHand();
    PlayerInteractEvent event = new PlayerInteractEvent(
        player,
        Action.RIGHT_CLICK_BLOCK,
        item,
        block,
        BlockFace.UP,
        hand
    );
    dispatch(event);
    return event.useInteractedBlock() != Event.Result.DENY;
  }

  public static boolean attemptBlockBreak(Player player, Block block) {
    if (!canAttemptBlockAction(player, block) || player.getWorld() != block.getWorld()) {
      return false;
    }

    return player.breakBlock(block);
  }

  public static boolean attemptBlockBreakProbe(Player player, Block block) {
    if (!canAttemptBlockAction(player, block) || player.getWorld() != block.getWorld()) {
      return false;
    }

    BlockBreakEvent event = new BlockBreakEvent(block, player);
    dispatch(event);
    return !event.isCancelled();
  }

  public static boolean attemptBlockPlaceProbe(Player player, Block block) {
    if (!canAttemptBlockAction(player, block) || player.getWorld() != block.getWorld()) {
      return false;
    }

    ItemStack item = player.getInventory().getItemInMainHand();
    BlockPlaceEvent event = new BlockPlaceEvent(
        block,
        block.getState(),
        block,
        item,
        player,
        true,
        EquipmentSlot.HAND
    );
    dispatch(event);
    return !event.isCancelled() && event.canBuild();
  }

  @SuppressWarnings("deprecation")
  public static boolean attemptItemPickup(Player player, Item item, int remaining) {
    return attemptItemPickup(player, item, remaining, null, true);
  }

  @SuppressWarnings("deprecation")
  public static boolean attemptBlockDropPickup(Player player, Item item, int remaining, Location source) {
    return attemptItemPickup(player, item, remaining, source, false);
  }

  @SuppressWarnings("deprecation")
  private static boolean attemptItemPickup(Player player, Item item, int remaining, Location source,
                                           boolean requireLiveEntity) {
    if (!isPickupContextValid(player, item, source, requireLiveEntity)) {
      return false;
    }

    ItemStack stack = item.getItemStack();
    if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
      return false;
    }

    int normalizedRemaining = Math.min(stack.getAmount(), Math.max(0, remaining));
    if (!dispatchAttemptPickup(player, item, normalizedRemaining)
        || normalizedRemaining >= stack.getAmount()) {
      return false;
    }

    ItemStack original = stack.clone();
    ItemStack visible = original.clone();
    visible.setAmount(original.getAmount() - normalizedRemaining);
    item.setItemStack(visible);
    try {
      PlayerPickupItemEvent playerEvent = new PlayerPickupItemEvent(player, item, normalizedRemaining);
      playerEvent.setCancelled(!player.getCanPickupItems());
      dispatch(playerEvent);
      if (playerEvent.isCancelled()) {
        return false;
      }

      EntityPickupItemEvent entityEvent = new EntityPickupItemEvent(player, item, normalizedRemaining);
      entityEvent.setCancelled(!player.getCanPickupItems());
      dispatch(entityEvent);
      return !entityEvent.isCancelled();
    } finally {
      restorePickupStack(item, visible, original, requireLiveEntity);
    }
  }

  public static int remainingAfterPickup(Inventory inventory, ItemStack stack) {
    int remaining = stack.getAmount();
    int maxStackSize = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
    for (ItemStack slot : inventory.getStorageContents()) {
      if (slot == null || slot.getType() == Material.AIR) {
        remaining -= maxStackSize;
      } else if (slot.isSimilar(stack)) {
        remaining -= Math.max(0, maxStackSize - slot.getAmount());
      }
      if (remaining <= 0) {
        return 0;
      }
    }
    return remaining;
  }

  private static void addContainerBlock(Set<Block> blocks, InventoryHolder holder) {
    if (holder instanceof BlockState blockState) {
      blocks.add(blockState.getBlock());
    }
  }

  private static boolean canAttemptBlockAction(Player player, Block block) {
    if (J.isFoliaThreading()) {
      if (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(block.getLocation())) {
        return false;
      }
      return player.isOnline() && player.getWorld() == block.getWorld();
    }
    return player.isOnline();
  }

  private static void addConnectedChest(Set<Block> blocks, Block target) {
    if (!(target.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) {
      return;
    }
    BlockFace facing = chest.getFacing();
    BlockFace connected = chest.getType() == Chest.Type.LEFT
        ? clockwise(facing)
        : counterClockwise(facing);
    blocks.add(target.getRelative(connected));
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

  private static boolean dispatchAttemptPickup(Player player, Item item, int remaining) {
    if (ATTEMPT_PICKUP_CONSTRUCTOR == null) {
      return true;
    }
    try {
      Event event = ATTEMPT_PICKUP_CONSTRUCTOR.newInstance(player, item, remaining);
      dispatch(event);
      return !(event instanceof Cancellable cancellable) || !cancellable.isCancelled();
    } catch (ReflectiveOperationException error) {
      error.printStackTrace();
      return false;
    }
  }

  private static Constructor<? extends Event> resolveAttemptPickupConstructor() {
    try {
      Class<? extends Event> eventType = Class.forName(ATTEMPT_PICKUP_EVENT).asSubclass(Event.class);
      return eventType.getConstructor(Player.class, Item.class, int.class);
    } catch (ReflectiveOperationException | LinkageError absent) {
      return null;
    }
  }

  private static boolean isPickupContextValid(Player player, Item item, Location source,
                                              boolean requireLiveEntity) {
    if (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player)
        || (requireLiveEntity
        ? !J.isOwnedByCurrentRegion(item)
        : source == null || !J.isOwnedByCurrentRegion(source)))) {
      return false;
    }
    if (!player.isOnline()
        || item.isDead()
        || (requireLiveEntity && !item.isValid())
        || player.getWorld() != item.getWorld()
        || (!requireLiveEntity && (source == null || source.getWorld() != item.getWorld()))) {
      return false;
    }
    return true;
  }

  private static void restorePickupStack(Item item, ItemStack visible, ItemStack original,
                                         boolean requireLiveEntity) {
    if (item.isDead() || (requireLiveEntity && !item.isValid())) {
      return;
    }
    ItemStack current = item.getItemStack();
    if (current != null && current.isSimilar(visible) && current.getAmount() == visible.getAmount()) {
      item.setItemStack(original);
    }
  }
}
