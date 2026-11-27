package art.arcane.adapt.api.protection;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.WorldRuntime;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Asks the server's protection plugins whether a player may act on a block or item before an adaptation does it.
 * Probes leave the world unchanged unless the method says otherwise. Call them on the thread that owns the target.
 */
@ApiStatus.Experimental
public final class ProtectionProbes {
  private ProtectionProbes() {
  }

  private static WorldRuntime world() {
    return Engine.bridge().world();
  }

  /** Breaks the block as the player, through the platform break event. True when it broke. */
  public static boolean attemptBlockBreak(PlayerRef player, BlockRef block) {
    return world().attemptBlockBreak(player, block);
  }

  /** True when no protection plugin vetoes the player breaking the block; the block stays. */
  public static boolean attemptBlockBreakProbe(PlayerRef player, BlockRef block) {
    return world().attemptBlockBreakProbe(player, block);
  }

  /** Fires an ordinary break event every listener sees; true when nobody cancelled it. The block stays. */
  public static boolean callBlockBreak(PlayerRef player, BlockRef block) {
    return world().callBlockBreak(player, block);
  }

  /** True when no protection plugin vetoes the player using (right-clicking) the block. */
  public static boolean attemptBlockUse(PlayerRef player, BlockRef block) {
    return world().attemptBlockUse(player, block);
  }

  /** True when no protection plugin vetoes the player placing at the block; nothing is placed. */
  public static boolean attemptBlockPlaceProbe(PlayerRef player, BlockRef block) {
    return world().attemptBlockPlaceProbe(player, block);
  }

  /** True when no protection plugin vetoes the player growing the tree feature into exactly these blocks. */
  public static boolean attemptTreeGrow(PlayerRef player, BlockRef origin, Id feature, List<BlockChange> blocks) {
    return world().attemptTreeGrow(player, origin, feature, blocks);
  }

  /** True when no protection plugin vetoes the player taking a block drop that has not spawned yet. */
  public static boolean attemptBlockDropPickup(PlayerRef player, ItemEntityRef item, int remaining, Loc source) {
    return world().attemptBlockDropPickup(player, item, remaining, source);
  }

  /** True when no protection plugin vetoes the player picking up a live item entity, leaving {@code remaining}. */
  public static boolean attemptItemPickup(PlayerRef player, ItemEntityRef item, int remaining) {
    return world().attemptItemPickup(player, item, remaining);
  }

  /** True when no protection plugin vetoes the non-player {@code entity} picking up the live {@code item}. */
  public static boolean attemptEntityItemPickup(LivingRef entity, ItemEntityRef item, int remaining) {
    return world().attemptEntityItemPickup(entity, item, remaining);
  }

  /** Items of {@code stack} that would not fit into the player's storage slots. */
  public static int remainingAfterPickup(PlayerInventoryRef inventory, ItemRef stack) {
    return world().remainingAfterPickup(inventory, stack);
  }

  /** Items of {@code stack} that would not fit into the storage slots of {@code inventory}. */
  public static int remainingAfterPickup(InventoryRef inventory, ItemRef stack) {
    return world().remainingAfterPickup(inventory, stack);
  }

  /**
   * The physical blocks of the container at {@code target}: the target, the other half of a connected chest and,
   * with {@code includeInventoryHalves}, both halves of the double chest inventory it opens.
   */
  public static List<BlockRef> containerBlocks(BlockRef target, boolean includeInventoryHalves) {
    return world().containerBlocks(target, includeInventoryHalves);
  }

  /** True when the player may use every one of the container blocks, probed in order. */
  public static boolean attemptContainerOpen(PlayerRef player, List<BlockRef> blocks) {
    return world().attemptContainerOpen(player, blocks);
  }
}
