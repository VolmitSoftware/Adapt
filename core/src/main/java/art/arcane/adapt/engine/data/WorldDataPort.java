package art.arcane.adapt.engine.data;

import art.arcane.adapt.platform.world.BlockRef;

import java.util.UUID;

/**
 * Per-block world data the framework reads and writes: diminishing XP earnings, placement provenance and block owners. On Bukkit it
 * is backed by the mantle-based {@code WorldData}; the neutral fallback stores nothing, so every block reads as
 * untouched and natural.
 */
public interface WorldDataPort {
  WorldDataPort NONE = new WorldDataPort() {
    @Override
    public double reportEarnings(BlockRef block) {
      return 1D;
    }

    @Override
    public double getEarningsMultiplier(BlockRef block) {
      return 1D;
    }

    @Override
    public Placement placement(BlockRef block) {
      return null;
    }

    @Override
    public void setPlacement(BlockRef block, Placement placement) {
    }

    @Override
    public void clearPlacement(BlockRef block) {
    }

    @Override
    public UUID blockOwner(BlockRef block) {
      return null;
    }

    @Override
    public void setBlockOwner(BlockRef block, UUID owner) {
    }
  };

  /** Counts one more earning at the block and returns the multiplier for it ({@code 1 / earnings}). */
  double reportEarnings(BlockRef block);

  /** Current earnings multiplier at the block without counting a new earning. */
  double getEarningsMultiplier(BlockRef block);

  /** Stored placement stamp, or null when the block has none. */
  Placement placement(BlockRef block);

  void setPlacement(BlockRef block, Placement placement);

  void clearPlacement(BlockRef block);

  /** Player who owns the block (a brewing stand's brewer), or null. Bukkit keeps it as the BrewingStandOwner matter. */
  UUID blockOwner(BlockRef block);

  /** Stores the block's owner; null removes it. */
  void setBlockOwner(BlockRef block, UUID owner);

  /** Placement stamp in epoch seconds; zero means unset. Mirrors the Bukkit {@code PlacementStamp} unit. */
  record Placement(int placedAt, int brokenAt, int bonemealedAt) {
  }
}
