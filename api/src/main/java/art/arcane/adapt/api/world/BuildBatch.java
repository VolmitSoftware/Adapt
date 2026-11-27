package art.arcane.adapt.api.world;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A multi-block edit an adaptation makes for a player, run by {@link BuildBatches} a few blocks per tick. Every block
 * goes through the player's protection checks and real place or break events, each placed block is paid for, and the
 * batch can be kept for undo. Build one with {@link #of}.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class BuildBatch {
  private final Adaptation<?> owner;
  private final PlayerRef player;
  private final List<Step> steps;
  private final int limit;
  private final int pace;
  private final @Nullable BuildPayment payment;
  private final int keep;
  private final Consumer<BuildResult> done;

  private BuildBatch(Builder builder) {
    owner = builder.owner;
    player = builder.player;
    steps = List.copyOf(builder.steps);
    limit = builder.limit;
    pace = builder.pace;
    payment = builder.payment;
    keep = builder.keep;
    done = builder.done;
  }

  /** Starts a batch {@code owner} makes for {@code player}. */
  public static Builder of(Adaptation<?> owner, PlayerRef player) {
    return new Builder(owner, player);
  }

  /** The adaptation the batch belongs to; protection checks and the undo journal go by it. */
  public Adaptation<?> owner() {
    return owner;
  }

  /** The player the batch builds for. */
  public PlayerRef player() {
    return player;
  }

  /** The planned changes, in the order they run. */
  public List<Step> steps() {
    return steps;
  }

  /** At most this many blocks change; {@link Integer#MAX_VALUE} for no limit. */
  public int limit() {
    return limit;
  }

  /** Steps tried per tick, before the server-wide cap. */
  public int pace() {
    return pace;
  }

  /** Where placed blocks are paid from; empty for the player's inventory. */
  public Optional<BuildPayment> payment() {
    return Optional.ofNullable(payment);
  }

  /** How many of this owner's batches the player's undo journal keeps; 0 when the batch is not undoable. */
  public int keep() {
    return keep;
  }

  /** What runs on the player's thread when the batch ends; does nothing unless set. */
  public Consumer<BuildResult> onDone() {
    return done;
  }

  /** One planned change. */
  public sealed interface Step permits Place, Break {
    /** The block the step changes. */
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

  /** Builds a {@link BuildBatch}; every setting is optional. */
  public static final class Builder {
    private final Adaptation<?> owner;
    private final PlayerRef player;
    private final List<Step> steps = new ArrayList<>();
    private int limit = Integer.MAX_VALUE;
    private int pace = 4;
    private @Nullable BuildPayment payment;
    private int keep;
    private Consumer<BuildResult> done = (BuildResult ignored) -> {
    };

    private Builder(Adaptation<?> owner, PlayerRef player) {
      this.owner = Objects.requireNonNull(owner, "owner");
      this.player = Objects.requireNonNull(player, "player");
    }

    /**
     * Places {@code state}, paid with one of the item that places it; skipped when the state has no item. Needs a
     * running core.
     */
    public Builder place(BlockRef block, BlockStateRef state) {
      Id item = state.placementItem();
      return item == null ? this : place(block, state, Engine.bridge().platform().host().items().create(item, 1));
    }

    /** Places {@code state}, paid with one item similar to {@code unit} (same type and data). */
    public Builder place(BlockRef block, BlockStateRef state, ItemRef unit) {
      steps.add(new Place(Objects.requireNonNull(block, "block"), Objects.requireNonNull(state, "state"),
          unit.copyWithAmount(1)));
      return this;
    }

    /** Breaks the block for real with the held tool, charging {@code durability} to it. */
    public Builder breakBlock(BlockRef block, int durability) {
      return breakWith(block, durability, (ItemRef held) -> true);
    }

    /** Breaks the block like {@link #breakBlock}, stopping the batch when the held tool no longer passes {@code tool}. */
    public Builder breakWith(BlockRef block, int durability, Predicate<ItemRef> tool) {
      steps.add(new Break(Objects.requireNonNull(block, "block"), Math.max(0, durability),
          Objects.requireNonNull(tool, "tool")));
      return this;
    }

    /** At most this many blocks are placed or broken; the rest stop the batch with {@link BuildResult.Stop#LIMIT}. */
    public Builder limit(int blocks) {
      limit = Math.max(0, blocks);
      return this;
    }

    /** Steps tried per tick for this batch, before the server-wide cap. Default 4. */
    public Builder pace(int perTick) {
      pace = Math.max(1, perTick);
      return this;
    }

    /** Pays from somewhere other than the player, such as {@link BuildPayment#container}. */
    public Builder payFrom(BuildPayment source) {
      payment = Objects.requireNonNull(source, "source");
      return this;
    }

    /** Records the placed blocks for undo, keeping the newest {@code batches} batches of this owner for the player. */
    public Builder undoable(int batches) {
      keep = Math.max(1, batches);
      return this;
    }

    /** Runs on the player's thread when the batch ends, however it ends. */
    public Builder onDone(Consumer<BuildResult> callback) {
      done = Objects.requireNonNull(callback, "callback");
      return this;
    }

    /** The finished batch; hand it to {@link BuildBatches#start(BuildBatch)}. */
    public BuildBatch build() {
      return new BuildBatch(this);
    }
  }
}
