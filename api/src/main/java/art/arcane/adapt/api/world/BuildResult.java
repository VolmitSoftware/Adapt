package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * What a finished build batch or undo did, and why it stopped. Adapt creates results; new accessors may be added.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface BuildResult {
  /** Blocks placed. */
  int placed();

  /** Blocks broken. */
  int broken();

  /** Blocks an undo took back. */
  int undone();

  /** Steps passed over because their block was occupied, already empty, unloaded or changed since. */
  int skipped();

  /** Why the run ended. */
  Stop stop();

  /** The blocks the batch placed, or the undo cleared, in order. */
  List<BlockRef> blocks();

  /** Why a build batch or undo ended. A {@code switch} over this enum needs a default branch: minor releases add constants. */
  enum Stop {
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
