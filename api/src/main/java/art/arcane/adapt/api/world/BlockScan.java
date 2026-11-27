package art.arcane.adapt.api.world;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A nearest-match block search around a point, spread over ticks under a server-wide read budget and run on the
 * thread that owns each chunk. Every block within {@link Builder#denseRadius dense radius} is read, nearest shell
 * first; beyond it, blocks out to {@link Builder#radius radius} are read in an order drawn from the
 * {@link Builder#seed seed} until {@link Builder#maxSamples} reads are spent. Matches at the same distance are ordered by
 * y, then x, then z, so a scan with a fixed seed over an unchanged world finds the same matches. A scan that is still
 * running after 100 ticks completes with what it found.
 *
 * <pre>{@code
 * BlockScan.around(player.location()).radius(24).denseRadius(6).maxSamples(6000).maxResults(8)
 *     .matching((Id block) -> block.path().endsWith("_ore"))
 *     .onComplete((BlockScan.Result result) -> show(player, result.matches()))
 *     .build()
 *     .submit(this, player.uuid());
 * }</pre>
 */
@ApiStatus.AvailableSince("1.0.0")
public final class BlockScan {
  private final Loc origin;
  private final int radius;
  private final int denseRadius;
  private final int maxSamples;
  private final int maxResults;
  private final int seed;
  private final List<Supplied> supplied;
  private final @Nullable Predicate<Id> matcher;
  private final @Nullable Predicate<BlockRef> blockMatcher;
  private final Consumer<Result> completion;

  private BlockScan(Builder builder) {
    origin = builder.origin;
    radius = builder.radius;
    denseRadius = builder.denseRadius;
    maxSamples = builder.maxSamples;
    maxResults = builder.maxResults;
    seed = builder.seed;
    supplied = List.copyOf(builder.supplied);
    matcher = builder.matcher;
    blockMatcher = builder.blockMatcher;
    completion = Objects.requireNonNull(builder.completion, "onComplete");
    if (matcher == null && blockMatcher == null) {
      throw new IllegalArgumentException("A block scan needs matching or matchingBlocks");
    }
    if (radius < 0 || denseRadius < 0 || maxSamples < 0) {
      throw new IllegalArgumentException("radius, denseRadius and maxSamples must not be negative");
    }
    if (maxResults < 1) {
      throw new IllegalArgumentException("maxResults must be positive");
    }
  }

  /** Starts a scan centred on {@code origin}, which must have a world. */
  public static Builder around(Loc origin) {
    return new Builder(origin);
  }

  /**
   * Starts the scan. A scan the same owner still runs for the same player is cancelled; one owner keeps at most one
   * scan per player. Call {@link #cancelAll(Object)} when the owner unregisters.
   *
   * @param owner the adaptation or other object the scan belongs to, compared by identity
   * @return the id the {@link Result} carries
   */
  public UUID submit(Object owner, UUID player) {
    return Engine.bridge().world().scan(Objects.requireNonNull(owner, "owner"),
        Objects.requireNonNull(player, "player"), this);
  }

  /** Cancels the owner's running scan for the player, if any; its completion never runs. */
  public static void cancel(Object owner, UUID player) {
    Engine.bridge().world().cancelScan(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(player, "player"));
  }

  /** Cancels every running scan of {@code owner}, for all players; their completions never run. */
  public static void cancelAll(Object owner) {
    Engine.bridge().world().cancelScans(Objects.requireNonNull(owner, "owner"));
  }

  /** The point distances are measured from. */
  public Loc origin() {
    return origin;
  }

  /** The farthest a match may be, in blocks. */
  public int radius() {
    return radius;
  }

  /** The distance out to which every block is read. */
  public int denseRadius() {
    return denseRadius;
  }

  /** The most blocks the scan reads in total. */
  public int maxSamples() {
    return maxSamples;
  }

  /** The most matches the result holds. */
  public int maxResults() {
    return maxResults;
  }

  /** The seed of the sampling order beyond the dense radius. */
  public int seed() {
    return seed;
  }

  /** Matches the caller supplied, which compete with found blocks by distance. */
  public List<Supplied> supplied() {
    return supplied;
  }

  /** The block type test, or null when only {@link #blockMatcher()} is set. */
  public @Nullable Predicate<Id> matcher() {
    return matcher;
  }

  /** The block test, or null when only {@link #matcher()} is set; wins when both are set. */
  public @Nullable Predicate<BlockRef> blockMatcher() {
    return blockMatcher;
  }

  /** What receives the {@link Result}. */
  public Consumer<Result> completion() {
    return completion;
  }

  /** One matching block. Adapt creates matches; new accessors may be added. */
  @ApiStatus.NonExtendable
  public interface Match {
    /** The block's x. */
    int x();

    /** The block's y. */
    int y();

    /** The block's z. */
    int z();

    /** The block's type. */
    Id block();

    /** The squared distance from the scan's origin. */
    double distanceSquared();

    /** True for a match from {@link Builder#supply}, which the scan did not read from the world. */
    boolean supplied();

    /** The centre of the block in {@code world}. */
    default Loc center(WorldRef world) {
      return new Loc(world, x() + 0.5D, y() + 0.5D, z() + 0.5D);
    }
  }

  /** A match the caller knows of without the world showing it, such as an ore vein another plugin hides. */
  public static final class Supplied {
    private final int x;
    private final int y;
    private final int z;
    private final Id block;
    private final double distanceSquared;

    private Supplied(int x, int y, int z, Id block, double distanceSquared) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.block = Objects.requireNonNull(block, "block");
      if (!Double.isFinite(distanceSquared) || distanceSquared < 0D) {
        throw new IllegalArgumentException("distanceSquared must be finite and non-negative, was " + distanceSquared);
      }
      this.distanceSquared = distanceSquared;
    }

    /**
     * A supplied match of type {@code block} at a block position.
     *
     * @param distanceSquared the squared distance from the scan's origin, finite and not negative
     */
    public static Supplied at(int x, int y, int z, Id block, double distanceSquared) {
      return new Supplied(x, y, z, block, distanceSquared);
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int z() {
      return z;
    }

    /** The type the match reports. */
    public Id block() {
      return block;
    }

    /** The squared distance from the scan's origin. */
    public double distanceSquared() {
      return distanceSquared;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Supplied that && x == that.x && y == that.y && z == that.z && block.equals(that.block)
          && Double.compare(distanceSquared, that.distanceSquared) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(x, y, z, block, distanceSquared);
    }

    @Override
    public String toString() {
      return "Supplied[" + block + " at " + x + "," + y + "," + z + ", distanceSquared=" + distanceSquared + "]";
    }
  }

  /**
   * What a scan found, nearest first, at most {@code maxResults}. Delivered on the global tick: hop to the player's
   * thread before touching the player or the world. Adapt creates results; new accessors may be added.
   */
  @ApiStatus.NonExtendable
  public interface Result {
    /** The id {@link #submit} returned. */
    UUID scan();

    /** The world that was scanned. */
    WorldRef world();

    /** The matches, nearest first, unmodifiable. */
    List<Match> matches();

    /** True when the scan ran out of time before it spent its samples. */
    boolean budgetExpired();
  }

  /** Builds a {@link BlockScan}; a matcher and {@link #onComplete} are required. */
  public static final class Builder {
    private final Loc origin;
    private int radius;
    private int denseRadius;
    private int maxSamples;
    private int maxResults = 1;
    private int seed = ThreadLocalRandom.current().nextInt();
    private List<Supplied> supplied = List.of();
    private @Nullable Predicate<Id> matcher;
    private @Nullable Predicate<BlockRef> blockMatcher;
    private @Nullable Consumer<Result> completion;

    private Builder(Loc origin) {
      this.origin = Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(origin.world(), "origin world");
    }

    /** The farthest a match may be, in blocks. */
    public Builder radius(int blocks) {
      radius = blocks;
      return this;
    }

    /** Out to this distance every block is read; capped by {@link #radius}. */
    public Builder denseRadius(int blocks) {
      denseRadius = blocks;
      return this;
    }

    /** The most blocks the scan reads in total. */
    public Builder maxSamples(int reads) {
      maxSamples = reads;
      return this;
    }

    /** The most matches to return, at least 1; 1 by default. */
    public Builder maxResults(int matches) {
      maxResults = matches;
      return this;
    }

    /**
     * The seed of the order blocks beyond the dense radius are read in; a fresh random seed by default. Pass a fixed
     * seed for a scan that must read the same blocks each time.
     */
    public Builder seed(int seed) {
      this.seed = seed;
      return this;
    }

    /** Matches to add to what the scan finds; those outside the world's height are dropped. */
    public Builder supply(List<Supplied> matches) {
      supplied = List.copyOf(Objects.requireNonNull(matches, "matches"));
      return this;
    }

    /** Matches by block type, the cheapest test. */
    public Builder matching(Predicate<Id> blockType) {
      matcher = Objects.requireNonNull(blockType, "blockType");
      return this;
    }

    /** Matches by the block itself, for tests that need its state; runs on the chunk's thread. Wins over {@link #matching}. */
    public Builder matchingBlocks(Predicate<BlockRef> block) {
      blockMatcher = Objects.requireNonNull(block, "block");
      return this;
    }

    /** Receives the result when the scan ends; required. */
    public Builder onComplete(Consumer<Result> completion) {
      this.completion = Objects.requireNonNull(completion, "completion");
      return this;
    }

    /**
     * The finished scan; call {@link BlockScan#submit} to run it.
     *
     * @throws NullPointerException     without {@link #onComplete}
     * @throws IllegalArgumentException without a matcher, for a negative radius, dense radius or sample count, or
     *                                  for fewer than 1 result
     */
    public BlockScan build() {
      return new BlockScan(this);
    }
  }
}
