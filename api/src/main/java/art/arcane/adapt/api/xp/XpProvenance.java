package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.WorldRuntime;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * Where a block came from, so XP is not paid for blocks a player placed or forced to grow. Multipliers are 1 for a
 * natural block and lower for a placed or bonemealed one.
 */
@ApiStatus.Experimental
public final class XpProvenance {
  private XpProvenance() {
  }

  private static WorldRuntime world() {
    return Engine.bridge().world();
  }

  /** Marks the block as placed by a player. */
  public static void recordPlacement(BlockRef block) {
    world().recordPlacement(block);
  }

  /** Whether a player placed the block. */
  public static boolean isPlayerPlaced(BlockRef block) {
    return world().isPlayerPlaced(block);
  }

  /**
   * Whether the block carries a placement record at all, whatever its age and whether provenance checks are on; for
   * telling player-built blocks from natural ones rather than for XP.
   */
  public static boolean wasPlaced(BlockRef block) {
    return world().wasPlaced(block);
  }

  /** Drops the block's placement record; true when there was one. */
  public static boolean forgetPlacement(BlockRef block) {
    return world().forgetPlacement(block);
  }

  /** True when a player changed the block in a way growth does not undo. */
  public static boolean hasPermanentPlayerModification(BlockRef block) {
    return world().hasPermanentPlayerModification(block);
  }

  /** Marks the block as grown with bone meal. */
  public static void recordBonemeal(BlockRef block) {
    world().recordBonemeal(block);
  }

  /** For placing a block here: 0 when a block was broken at this spot only moments ago, else 1. */
  public static double placeXpMultiplier(BlockRef block) {
    return world().placeXpMultiplier(block);
  }

  /** For breaking the block: 0 when a player placed it or it replaces a block broken moments ago, else 1. */
  public static double breakXpMultiplier(BlockRef block) {
    return world().breakXpMultiplier(block);
  }

  /** For harvesting the block: the configured bone meal multiplier when it was bonemealed, else 1. */
  public static double harvestXpMultiplier(BlockRef block) {
    return world().harvestXpMultiplier(block);
  }

  /**
   * The XP multiplier for a block the player just placed: above 1 while they keep building onto their own blocks in
   * new spots, 1 otherwise. Each call updates the player's building streak.
   */
  public static double adjacencyBonusMultiplier(PlayerRef player, BlockRef placed) {
    return Engine.bridge().progress().adjacencyBonusMultiplier(player, placed);
  }

  /** The XP multiplier for a crop the player harvests: below 1 when they keep harvesting the same small field. */
  public static double fieldCycleMultiplier(PlayerRef player, BlockRef crop) {
    return Engine.bridge().progress().fieldCycleMultiplier(player, crop);
  }
}
