package art.arcane.adapt.engine.batch;

import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.adaptation.BonusBlockBreaks;
import art.arcane.adapt.engine.protection.ProtectionProbes;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Runs build batches and undoes them. Each player runs at most one batch or undo at a time, a few steps per tick on
 * their own thread, and all of them together stay under {@code [mutations] buildBlocksPerTick}. A batch that
 * {@linkplain BuildBatch.Builder#undoable records undo} is written to the player's journal when it ends, so its undo
 * survives a restart; an undo returns exactly the item each block cost, and only for a block whose type and item
 * count are still the ones placed (a container only when empty). An undo may be kept for a while so it can be
 * {@linkplain #redo redone}: the cleared blocks go back as a batch paid again from the player's inventory.
 */
public final class BuildBatches {
  private static final Id AIR = Id.minecraft("air");
  private static volatile BuildBatches current = new BuildBatches();

  private final BuildBudget budget = new BuildBudget();
  private final Map<UUID, Run> running = new ConcurrentHashMap<>();
  private final Map<UUID, BuildJournal> journals = new ConcurrentHashMap<>();
  private final Map<UUID, Undone> redoable = new ConcurrentHashMap<>();

  private BuildBatches() {
  }

  public static BuildBatches get() {
    return current;
  }

  /** A fresh service for a starting runtime; the previous one's runs are cancelled and its journals saved. */
  public static BuildBatches startRuntime() {
    current.shutdown();
    current = new BuildBatches();
    return current;
  }

  /** Cancels every run; what they placed so far is journaled. */
  public void shutdown() {
    for (Run run : List.copyOf(running.values())) {
      run.end(BuildResult.Stop.CANCELLED);
    }
  }

  /** Starts the batch; false when the player already runs one or the batch is empty. */
  public boolean start(BuildBatch batch) {
    if (batch.steps().isEmpty()) {
      return false;
    }
    return begin(new BuildRun(batch));
  }

  /**
   * Undoes the player's newest batch made by one of {@code owners} within {@code maxAgeMillis}, paced like a batch.
   * False when nothing can be undone or the player already runs something. {@code done} gets the counts.
   */
  public boolean undo(PlayerRef player, Collection<String> owners, long maxAgeMillis, int pace,
                      Consumer<BuildResult> done) {
    return undo(player, owners, maxAgeMillis, pace, 0L, done);
  }

  /** As {@link #undo(PlayerRef, Collection, long, int, Consumer)}, kept {@code redoMillis} for one {@link #redo}. */
  public boolean undo(PlayerRef player, Collection<String> owners, long maxAgeMillis, int pace, long redoMillis,
                      Consumer<BuildResult> done) {
    if (running.containsKey(player.uuid())) {
      return false;
    }
    BuildJournal.Batch batch = journal(player.uuid()).take(owners, maxAgeMillis);
    if (batch == null) {
      return false;
    }
    Consumer<BuildResult> kept = redoMillis <= 0L ? done : (BuildResult result) -> {
      if (!result.cleared().isEmpty()) {
        redoable.put(player.uuid(), new Undone(result.cleared(), System.currentTimeMillis() + redoMillis));
      }
      done.accept(result);
    };
    return begin(new UndoRun(player, batch, Math.max(1, pace), kept));
  }

  /**
   * Places back the player's kept undo when one of its blocks lies within {@code radius} of {@code near}, as a batch of
   * {@code owner} paid from the inventory. The undo is used up either way once it starts. False when none is kept,
   * it ran out, it is elsewhere, or the player already runs something.
   */
  public boolean redo(PlayerRef player, Adaptation<?> owner, BlockRef near, double radius, int pace,
                      Consumer<BuildResult> done) {
    Undone undone = redoable.get(player.uuid());
    if (undone == null || running.containsKey(player.uuid())) {
      return false;
    }
    if (undone.expiresAt() < System.currentTimeMillis()) {
      redoable.remove(player.uuid(), undone);
      return false;
    }
    if (!undone.near(near, radius)) {
      return false;
    }
    BuildBatch.Builder batch = BuildBatch.of(owner, player).pace(Math.max(1, pace)).onDone(done);
    for (BuildBatch.Place place : undone.cleared()) {
      batch.place(place.block(), place.state(), place.unit());
    }
    if (!start(batch.build())) {
      return false;
    }
    redoable.remove(player.uuid(), undone);
    return true;
  }

  public boolean isRunning(PlayerRef player) {
    return running.containsKey(player.uuid());
  }

  /** Stops the player's batch or undo; what it changed so far stays and is journaled. */
  public void cancel(PlayerRef player) {
    Run run = running.get(player.uuid());
    if (run != null) {
      run.end(BuildResult.Stop.CANCELLED);
    }
  }

  /** How many undoable batches the player's journal holds. */
  public int undoable(PlayerRef player) {
    return journal(player.uuid()).size();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    cancel(e.player());
    journals.remove(id);
    redoable.remove(id);
  }

  private boolean begin(Run run) {
    PlayerRef player = run.player;
    if (running.putIfAbsent(player.uuid(), run) != null) {
      return false;
    }
    run.task = Tasks.repeatEntity(player, run::tick, 0, 1, () -> run.end(BuildResult.Stop.CANCELLED));
    if (run.task == null) {
      run.end(BuildResult.Stop.CANCELLED);
      return false;
    }
    return true;
  }

  private BuildJournal journal(UUID player) {
    return journals.computeIfAbsent(player, BuildJournal::load);
  }

  private abstract class Run {
    final PlayerRef player;
    final int pace;
    int next;
    int placed;
    int broken;
    int undone;
    int skipped;
    final List<BlockRef> changed = new ArrayList<>();
    final List<BuildBatch.Place> cleared = new ArrayList<>();
    @Nullable TaskHandle task;
    private boolean ended;

    Run(PlayerRef player, int pace) {
      this.player = player;
      this.pace = pace;
    }

    abstract int size();

    /** Runs step {@code index}; a stop ends the run there. */
    abstract @Nullable BuildResult.Stop step(int index);

    abstract void finished(BuildResult result);

    void tick() {
      if (ended) {
        return;
      }
      if (!player.isOnline()) {
        end(BuildResult.Stop.CANCELLED);
        return;
      }
      int granted = budget.claim(Math.min(pace, size() - next));
      for (int done = 0; done < granted && next < size(); done++) {
        BuildResult.Stop stop = step(next);
        next++;
        if (stop != null) {
          end(stop);
          return;
        }
      }
      if (next >= size()) {
        end(BuildResult.Stop.DONE);
      }
    }

    synchronized void end(BuildResult.Stop stop) {
      if (ended) {
        return;
      }
      ended = true;
      if (task != null) {
        task.cancel();
      }
      running.remove(player.uuid(), this);
      finished(new BuildResult(placed, broken, undone, skipped, stop, changed, cleared));
    }
  }

  private final class BuildRun extends Run {
    private final BuildBatch batch;
    private final Adaptation<?> owner;
    private final @Nullable BuildJournal.Builder journaled;

    BuildRun(BuildBatch batch) {
      super(batch.player(), batch.pace());
      this.batch = batch;
      this.owner = batch.owner();
      this.journaled = batch.keep() > 0 ? new BuildJournal.Builder(owner.getId().toString()) : null;
    }

    @Override
    int size() {
      return batch.steps().size();
    }

    @Override
    @Nullable BuildResult.Stop step(int index) {
      if (placed + broken >= batch.limit()) {
        return BuildResult.Stop.LIMIT;
      }
      BuildBatch.Step step = batch.steps().get(index);
      BlockRef block = step.block();
      if (!block.world().equals(player.world())) {
        skipped++;
        return null;
      }
      if (!Tasks.isOwnedByCurrentRegion(block.location())) {
        return BuildResult.Stop.OUT_OF_REACH;
      }
      return switch (step) {
        case BuildBatch.Place place -> place(place);
        case BuildBatch.Break hit -> breakBlock(hit);
      };
    }

    private @Nullable BuildResult.Stop place(BuildBatch.Place step) {
      BlockRef block = step.block();
      if (!block.isAir() && !block.isReplaceable()) {
        skipped++;
        return null;
      }
      if (!owner.canBlockPlace(player, block.location()) || !ProtectionProbes.attemptBlockPlaceProbe(player, block)) {
        return BuildResult.Stop.PROTECTED;
      }
      ItemRef[] taken = new ItemRef[1];
      if (!owner.payItemCost(player, "block", step.unit(), 1, () -> {
        taken[0] = batch.payment().take(step.unit());
        return taken[0] != null;
      })) {
        return BuildResult.Stop.NO_ITEMS;
      }
      BlockStateRef replaced = block.state();
      block.setState(step.state(), true);
      if (!block.type().equals(step.state().type())
          || !ProtectionProbes.callPlace(player, block, replaced, block.relative(BlockFace.DOWN),
          taken[0] == null ? step.unit() : taken[0])) {
        block.setState(replaced, false);
        if (taken[0] != null) {
          batch.payment().giveBack(taken[0]);
        }
        return BuildResult.Stop.PROTECTED;
      }
      XpProvenance.recordPlacement(block);
      if (journaled != null) {
        journaled.add(block, taken[0]);
      }
      changed.add(block);
      placed++;
      return null;
    }

    private @Nullable BuildResult.Stop breakBlock(BuildBatch.Break step) {
      BlockRef block = step.block();
      if (block.isAir()) {
        skipped++;
        return null;
      }
      ItemRef held = player.inventory().mainHand();
      if (!step.tool().test(held) || !BonusBreaks.canCharge(held, step.durability())) {
        return BuildResult.Stop.TOOL_WORN;
      }
      if (!owner.canBlockBreak(player, block.location()) || !BonusBlockBreaks.breakBlock(player, block, step.durability())) {
        return BuildResult.Stop.PROTECTED;
      }
      broken++;
      return null;
    }

    @Override
    void finished(BuildResult result) {
      if (journaled != null && !journaled.isEmpty()) {
        journal(player.uuid()).record(journaled.build(), batch.keep());
      }
      batch.finish(result);
    }
  }

  private final class UndoRun extends Run {
    private final BuildJournal.Batch batch;
    private final @Nullable WorldRef world;
    private final Consumer<BuildResult> done;

    UndoRun(PlayerRef player, BuildJournal.Batch batch, int pace, Consumer<BuildResult> done) {
      super(player, pace);
      this.batch = batch;
      this.world = AdaptCore.platform().host().world(batch.world());
      this.done = done;
    }

    @Override
    int size() {
      return batch.size();
    }

    @Override
    @Nullable BuildResult.Stop step(int index) {
      int entry = batch.size() - 1 - index;
      if (world == null || !world.isLoaded() || !world.isChunkLoaded(batch.x(entry) >> 4, batch.z(entry) >> 4)) {
        skipped++;
        return null;
      }
      BlockRef block = world.blockAt(batch.x(entry), batch.y(entry), batch.z(entry));
      if (!Tasks.isOwnedByCurrentRegion(block.location())) {
        return BuildResult.Stop.OUT_OF_REACH;
      }
      if (!BuildJournal.stateKey(block.state()).equals(batch.stateKey(entry)) || holdsItems(block)) {
        skipped++;
        return null;
      }
      if (!ProtectionProbes.callRemove(player, block)) {
        return BuildResult.Stop.PROTECTED;
      }
      BlockStateRef state = block.state();
      block.setType(AIR, true);
      XpProvenance.recordBreak(block);
      ItemRef paid = batch.paid(entry);
      if (paid != null) {
        Payments.give(player, List.of(paid));
        cleared.add(new BuildBatch.Place(block, state, paid.copyWithAmount(1)));
      }
      changed.add(block);
      undone++;
      return null;
    }

    private boolean holdsItems(BlockRef block) {
      InventoryRef container = block.container();
      if (container == null) {
        return false;
      }
      for (ItemRef item : container.contents()) {
        if (item != null && !item.isEmpty()) {
          return true;
        }
      }
      return false;
    }

    @Override
    void finished(BuildResult result) {
      done.accept(result);
    }
  }

  /** An undo kept for one redo: the steps that place back what it cleared, until {@code expiresAt}. */
  private record Undone(List<BuildBatch.Place> cleared, long expiresAt) {
    boolean near(BlockRef center, double radius) {
      double limit = radius * radius;
      for (BuildBatch.Place place : cleared) {
        BlockRef block = place.block();
        if (block.world().equals(center.world())) {
          double dx = block.x() - center.x();
          double dy = block.y() - center.y();
          double dz = block.z() - center.z();
          if ((dx * dx) + (dy * dy) + (dz * dz) <= limit) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
