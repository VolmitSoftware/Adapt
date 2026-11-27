package art.arcane.adapt.engine.batch;

import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;

/**
 * What a finished build batch or undo did, and why it stopped. {@code blocks} are the blocks the batch placed, or the
 * undo cleared, in order; {@code cleared} are the steps that would place back what an undo cleared, each paid with the
 * item it was refunded.
 */
public record BuildResult(int placed, int broken, int undone, int skipped, Stop stop, List<BlockRef> blocks,
                          List<BuildBatch.Place> cleared) {
  public BuildResult {
    blocks = List.copyOf(blocks);
    cleared = List.copyOf(cleared);
  }

  public BuildResult(int placed, int broken, int undone, int skipped, Stop stop, List<BlockRef> blocks) {
    this(placed, broken, undone, skipped, stop, blocks, List.of());
  }

  public BuildResult(int placed, int broken, int undone, int skipped, Stop stop) {
    this(placed, broken, undone, skipped, stop, List.of(), List.of());
  }

  public enum Stop {
    /** Every step ran. */
    DONE,
    /** A protection plugin, region rule or place event refused a block; the batch stopped there. */
    PROTECTED,
    /** No item was left to pay for the next block. */
    NO_ITEMS,
    /** The next break would have broken the held tool. */
    TOOL_WORN,
    /** The batch reached its block limit. */
    LIMIT,
    /** The next block is outside what this thread may change (another Folia region). */
    OUT_OF_REACH,
    /** The player left or the batch was cancelled. */
    CANCELLED
  }
}
