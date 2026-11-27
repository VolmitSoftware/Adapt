package art.arcane.adapt.api.world;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runs {@link BuildBatch}es, one at a time per player, under a server-wide budget of blocks per tick, and keeps each
 * player's undo journal. Builds and undos share the one slot per player. Needs a running core.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class BuildBatches {
  private BuildBatches() {
  }

  /** Starts the batch; false when the player already runs one or the batch is empty. */
  public static boolean start(BuildBatch batch) {
    return Engine.bridge().builds().start(Objects.requireNonNull(batch, "batch"));
  }

  /**
   * Undoes the player's newest undoable batch made by one of {@code owners} within {@code maxAgeMillis}, paced like a
   * batch, and refunds what it placed. With {@code redoMillis} above 0 the undo is kept that long for one
   * {@link #redo}. False when nothing can be undone or the player already runs something.
   */
  public static boolean undo(PlayerRef player, Collection<Id> owners, long maxAgeMillis, int pace, long redoMillis,
                             Consumer<BuildResult> done) {
    return Engine.bridge().builds().undo(Objects.requireNonNull(player, "player"), Objects.requireNonNull(owners, "owners"),
        maxAgeMillis, Math.max(1, pace), redoMillis, Objects.requireNonNull(done, "done"));
  }

  /**
   * Places back the player's kept undo when one of its blocks lies within {@code radius} of {@code near}, as a batch
   * of {@code owner} paid from the inventory. False when none is kept, it ran out, it is elsewhere, or the player
   * already runs something.
   */
  public static boolean redo(PlayerRef player, Adaptation<?> owner, BlockRef near, double radius, int pace,
                             Consumer<BuildResult> done) {
    return Engine.bridge().builds().redo(Objects.requireNonNull(player, "player"), Objects.requireNonNull(owner, "owner"),
        Objects.requireNonNull(near, "near"), radius, Math.max(1, pace), Objects.requireNonNull(done, "done"));
  }

  /** Whether a batch, undo or redo of the player is running now. */
  public static boolean isRunning(PlayerRef player) {
    return Engine.bridge().builds().isRunning(Objects.requireNonNull(player, "player"));
  }

  /** Stops the player's batch or undo; what it changed so far stays and is journaled. */
  public static void cancel(PlayerRef player) {
    Engine.bridge().builds().cancel(Objects.requireNonNull(player, "player"));
  }

  /** How many undoable batches the player's journal holds, of every owner. */
  public static int undoable(PlayerRef player) {
    return Engine.bridge().builds().undoable(Objects.requireNonNull(player, "player"));
  }
}
