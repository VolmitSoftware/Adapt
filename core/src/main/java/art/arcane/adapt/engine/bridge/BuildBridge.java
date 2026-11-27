package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.BuildRuntime;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildPayment;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.engine.batch.BuildBatches;
import art.arcane.adapt.engine.batch.Payment;
import art.arcane.adapt.engine.batch.Payments;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** {@link BuildRuntime} over the engine's build batches, which own the budget, the journal and the protection checks. */
final class BuildBridge implements BuildRuntime {
  @Override
  public boolean start(BuildBatch batch) {
    art.arcane.adapt.engine.batch.BuildBatch.Builder engine =
        art.arcane.adapt.engine.batch.BuildBatch.of(batch.owner(), batch.player())
            .limit(batch.limit())
            .pace(batch.pace())
            .onDone(relay(batch.onDone()));
    Map<ItemRef, ItemRef> faceUnits = new IdentityHashMap<>();
    batch.payment().ifPresent((BuildPayment payment) -> engine.payFrom(engine(payment, faceUnits)));
    if (batch.keep() > 0) {
      engine.undoable(batch.keep());
    }
    for (BuildBatch.Step step : batch.steps()) {
      if (step instanceof BuildBatch.Place place) {
        engine.place(place.block(), place.state(), place.unit());
      } else if (step instanceof BuildBatch.Break broken) {
        engine.breakWith(broken.block(), broken.durability(), broken.tool());
      }
    }
    art.arcane.adapt.engine.batch.BuildBatch built = engine.build();
    List<art.arcane.adapt.engine.batch.BuildBatch.Step> engineSteps = built.steps();
    for (int index = 0; index < engineSteps.size(); index++) {
      if (engineSteps.get(index) instanceof art.arcane.adapt.engine.batch.BuildBatch.Place copied
          && batch.steps().get(index) instanceof BuildBatch.Place place) {
        faceUnits.put(copied.unit(), place.unit());
      }
    }
    return BuildBatches.get().start(built);
  }

  @Override
  public boolean undo(PlayerRef player, Collection<Id> owners, long maxAgeMillis, int pace, long redoMillis,
                      Consumer<BuildResult> done) {
    List<String> keys = new ArrayList<>(owners.size());
    for (Id owner : owners) {
      keys.add(owner.toString());
    }
    return BuildBatches.get().undo(player, keys, maxAgeMillis, pace, redoMillis, relay(done));
  }

  @Override
  public boolean redo(PlayerRef player, Adaptation<?> owner, BlockRef near, double radius, int pace,
                      Consumer<BuildResult> done) {
    return BuildBatches.get().redo(player, owner, near, radius, pace, relay(done));
  }

  @Override
  public boolean isRunning(PlayerRef player) {
    return BuildBatches.get().isRunning(player);
  }

  @Override
  public void cancel(PlayerRef player) {
    BuildBatches.get().cancel(player);
  }

  @Override
  public int undoable(PlayerRef player) {
    return BuildBatches.get().undoable(player);
  }

  @Override
  public BuildPayment playerPayment(PlayerRef player) {
    return new EnginePayment(Payments.player(player));
  }

  @Override
  public BuildPayment containerPayment(InventoryRef container, PlayerRef owner) {
    return new EnginePayment(Payments.container(container, owner));
  }

  @Override
  public void give(PlayerRef player, List<ItemRef> items) {
    Payments.give(player, items);
  }

  private static BuildResult result(art.arcane.adapt.engine.batch.BuildResult result) {
    return new Finished(result.placed(), result.broken(), result.undone(), result.skipped(),
        BuildResult.Stop.valueOf(result.stop().name()), List.copyOf(result.blocks()));
  }

  private record Finished(int placed, int broken, int undone, int skipped, Stop stop, List<BlockRef> blocks)
      implements BuildResult {
  }

  private static Consumer<art.arcane.adapt.engine.batch.BuildResult> relay(Consumer<BuildResult> done) {
    return (art.arcane.adapt.engine.batch.BuildResult result) -> done.accept(result(result));
  }

  /**
   * The engine copies each step's unit, so a custom payment is handed back the face step's own unit, the one its
   * author built the batch with.
   */
  private static Payment engine(BuildPayment payment, Map<ItemRef, ItemRef> faceUnits) {
    if (payment instanceof EnginePayment wrapped) {
      return wrapped.engine();
    }
    return new Payment() {
      @Override
      public @Nullable ItemRef take(ItemRef unit) {
        return payment.take(faceUnits.getOrDefault(unit, unit));
      }

      @Override
      public void giveBack(ItemRef taken) {
        payment.giveBack(taken);
      }
    };
  }

  private record EnginePayment(Payment engine) implements BuildPayment {
    @Override
    public @Nullable ItemRef take(ItemRef unit) {
      return engine.take(unit);
    }

    @Override
    public void giveBack(ItemRef taken) {
      engine.giveBack(taken);
    }
  }
}
