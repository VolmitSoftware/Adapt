package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildPayment;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** What the engine does for build batches: paced placement, undo and redo, and block payment. */
@ApiStatus.Internal
public interface BuildRuntime {
  boolean start(BuildBatch batch);

  boolean undo(PlayerRef player, Collection<Id> owners, long maxAgeMillis, int pace, long redoMillis,
               Consumer<BuildResult> done);

  boolean redo(PlayerRef player, Adaptation<?> owner, BlockRef near, double radius, int pace,
               Consumer<BuildResult> done);

  boolean isRunning(PlayerRef player);

  void cancel(PlayerRef player);

  int undoable(PlayerRef player);

  BuildPayment playerPayment(PlayerRef player);

  BuildPayment containerPayment(InventoryRef container, PlayerRef owner);

  void give(PlayerRef player, List<ItemRef> items);
}
