package art.arcane.adapt.engine.batch;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A list of real block placements and breaks one player's tool makes, run a few per tick by {@link BuildBatches}.
 * Every placement takes its item from the {@link Payment} (the player's hands, then inventory, by default) through the
 * owner adaptation's ability cost, passes the region rules, the protection probe and a real place event that
 * protection and logging plugins see, and is recorded as player-placed. Every break is a real break with the held tool
 * ({@link art.arcane.adapt.engine.adaptation.BonusBlockBreaks}). A batch made {@link Builder#undoable undoable} records
 * what each placed block cost, for {@link BuildBatches#undo}.
 *
 * <p>Build one with {@link #of}, then {@link BuildBatches#start}. Place single-block states only (no beds or doors).
 */
public final class BuildBatch {
  private final Adaptation<?> owner;
  private final PlayerRef player;
  private final List<Step> steps;
  private final int limit;
  private final int pace;
  private final Payment payment;
  private final int keep;
  private final Consumer<BuildResult> done;

  private BuildBatch(Builder builder) {
    owner = builder.owner;
    player = builder.player;
    steps = List.copyOf(builder.steps);
    limit = builder.limit;
    pace = builder.pace;
    payment = builder.payment == null ? Payments.player(builder.player) : builder.payment;
    keep = builder.keep;
    done = builder.done;
  }

  public static Builder of(Adaptation<?> owner, PlayerRef player) {
    return new Builder(owner, player);
  }

  public Adaptation<?> owner() {
    return owner;
  }

  public PlayerRef player() {
    return player;
  }

  public List<Step> steps() {
    return steps;
  }

  int limit() {
    return limit;
  }

  int pace() {
    return pace;
  }

  Payment payment() {
    return payment;
  }

  int keep() {
    return keep;
  }

  void finish(BuildResult result) {
    done.accept(result);
  }

  /** One planned change. */
  public sealed interface Step permits Place, Break {
    BlockRef block();
  }

  /** Places {@code state} at {@code block}, paid with one item similar to {@code unit}. */
  public record Place(BlockRef block, BlockStateRef state, ItemRef unit) implements Step {
  }

  /**
   * Breaks {@code block} as the player with the main hand tool, charging {@code durability} more to it, while that
   * tool passes {@code tool}.
   */
  public record Break(BlockRef block, int durability, Predicate<ItemRef> tool) implements Step {
  }

  public static final class Builder {
    private final Adaptation<?> owner;
    private final PlayerRef player;
    private final List<Step> steps = new ArrayList<>();
    private int limit = Integer.MAX_VALUE;
    private int pace = 4;
    private @Nullable Payment payment;
    private int keep;
    private Consumer<BuildResult> done = (BuildResult ignored) -> {
    };

    private Builder(Adaptation<?> owner, PlayerRef player) {
      this.owner = Objects.requireNonNull(owner);
      this.player = Objects.requireNonNull(player);
    }

    /** Places {@code state}, paid with one of the item that places it; skipped when the state has no item. */
    public Builder place(BlockRef block, BlockStateRef state) {
      Id item = state.placementItem();
      return item == null ? this : place(block, state, AdaptCore.platform().host().items().create(item, 1));
    }

    /** Places {@code state}, paid with one item similar to {@code unit} (same type and data). */
    public Builder place(BlockRef block, BlockStateRef state, ItemRef unit) {
      steps.add(new Place(block, state, unit.copyWithAmount(1)));
      return this;
    }

    /** Breaks the block for real with the held tool, charging {@code durability} to it. */
    public Builder breakBlock(BlockRef block, int durability) {
      return breakWith(block, durability, (ItemRef held) -> true);
    }

    /** Breaks the block like {@link #breakBlock}, stopping the batch when the held tool no longer passes {@code tool}. */
    public Builder breakWith(BlockRef block, int durability, Predicate<ItemRef> tool) {
      steps.add(new Break(block, Math.max(0, durability), Objects.requireNonNull(tool)));
      return this;
    }

    /** At most this many blocks are placed or broken; the rest stop the batch with {@link BuildResult.Stop#LIMIT}. */
    public Builder limit(int blocks) {
      limit = Math.max(0, blocks);
      return this;
    }

    /** Steps tried per tick for this batch, before the server-wide cap. */
    public Builder pace(int perTick) {
      pace = Math.max(1, perTick);
      return this;
    }

    /** Pays from somewhere other than the player, such as {@link Payments#container}. */
    public Builder payFrom(Payment source) {
      payment = Objects.requireNonNull(source);
      return this;
    }

    /** Records the placed blocks for undo, keeping the newest {@code batches} batches of this owner for the player. */
    public Builder undoable(int batches) {
      keep = Math.max(1, batches);
      return this;
    }

    /** Runs on the player's thread when the batch ends, however it ends. */
    public Builder onDone(Consumer<BuildResult> callback) {
      done = Objects.requireNonNull(callback);
      return this;
    }

    public BuildBatch build() {
      return new BuildBatch(this);
    }
  }
}
