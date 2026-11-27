package art.arcane.adapt.engine.fx;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class FxViewers {
  public static final double DEFAULT_CULL_RADIUS = 24.0D;
  public static final double MAX_CULL_RADIUS = 48.0D;
  private static final int DISABLED_VIEWER_INDEX = -2;
  private static final int UNINDEXED_VIEWER_INDEX = -1;
  private static final int CELL_SHIFT = 5;
  private static final long SNAPSHOT_REFRESH_TICKS = 5L;
  private static final Snapshot EMPTY = new Snapshot(Long.MIN_VALUE, new PlayerRef[0], new double[0], new double[0], new double[0], Map.of(), Map.of());
  private static final Object BUILD_LOCK = new Object();
  private static final AtomicLong TICK_STAMP = new AtomicLong();
  private static volatile Snapshot snapshot = EMPTY;

  private FxViewers() {
  }

  public static void dispatch(Collection<PlayerRef> viewers, Consumer<PlayerRef> action) {
    if (viewers == null || action == null) {
      return;
    }
    FxDispatch.Emission emission = FxDispatch.emission(action::accept, null);
    Snapshot current = current();
    for (PlayerRef viewer : viewers) {
      if (viewer != null && !current.dispatch(viewer, emission) && current.shouldFallback(viewer)) {
        FxDispatch.dispatch(viewer, emission);
      }
    }
  }

  public static void dispatch(WorldRef world, double x, double y, double z, double radius, Consumer<PlayerRef> action) {
    if (action == null) {
      return;
    }
    current().dispatch(world, x, y, z, radius, FxDispatch.emission(action::accept, null));
  }

  static boolean dispatch(PlayerRef viewer, FxDispatch.Emission emission) {
    return current().dispatch(viewer, emission);
  }

  static boolean shouldFallback(PlayerRef viewer) {
    return current().shouldFallback(viewer);
  }

  static void bumpTick() {
    TICK_STAMP.incrementAndGet();
  }

  static void reset() {
    TICK_STAMP.set(0L);
    snapshot = EMPTY;
  }

  static Snapshot current() {
    long stamp = TICK_STAMP.get();
    Snapshot current = snapshot;
    if (isFresh(current, stamp)) {
      return current;
    }

    synchronized (BUILD_LOCK) {
      current = snapshot;
      stamp = TICK_STAMP.get();
      if (isFresh(current, stamp)) {
        return current;
      }

      Snapshot built = build(stamp);
      snapshot = built;
      return built;
    }
  }

  private static boolean isFresh(Snapshot current, long stamp) {
    return current.stamp != Long.MIN_VALUE
        && stamp >= current.stamp
        && stamp - current.stamp < SNAPSHOT_REFRESH_TICKS;
  }

  private static Snapshot build(long stamp) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    if (server == null) {
      return emptySnapshot(stamp);
    }

    List<AdaptPlayer> adaptPlayers = server.getOnlineAdaptPlayerSnapshot();
    int capacity = adaptPlayers.size();
    PlayerRef[] players = new PlayerRef[capacity];
    double[] xs = new double[capacity];
    double[] ys = new double[capacity];
    double[] zs = new double[capacity];
    Map<UUID, Map<Long, int[]>> cells = new HashMap<>(4);
    Map<UUID, Integer> dispatchIndices = new HashMap<>(capacity);
    int count = 0;

    for (int i = 0; i < adaptPlayers.size() && count < capacity; i++) {
      AdaptPlayer adaptPlayer = adaptPlayers.get(i);
      if (adaptPlayer == null || adaptPlayer.getData() == null || adaptPlayer.getPlayer() == null) {
        continue;
      }
      PlayerRef player = adaptPlayer.getPlayer();
      boolean effectsEnabled = adaptPlayer.getData().isEffectsEnabled();
      if (!effectsEnabled) {
        dispatchIndices.put(player.uuid(), DISABLED_VIEWER_INDEX);
        continue;
      }
      int playerIndex = count;
      count = index(adaptPlayer, players, xs, ys, zs, cells, count);
      dispatchIndices.put(player.uuid(), count > playerIndex ? playerIndex : UNINDEXED_VIEWER_INDEX);
    }

    if (count < capacity) {
      players = Arrays.copyOf(players, count);
      xs = Arrays.copyOf(xs, count);
      ys = Arrays.copyOf(ys, count);
      zs = Arrays.copyOf(zs, count);
    }

    return new Snapshot(stamp, players, xs, ys, zs, cells, dispatchIndices);
  }

  private static Snapshot emptySnapshot(long stamp) {
    return new Snapshot(stamp, new PlayerRef[0], new double[0], new double[0], new double[0], Map.of(), Map.of());
  }

  private static int index(AdaptPlayer adaptPlayer, PlayerRef[] players, double[] xs, double[] ys, double[] zs,
                           Map<UUID, Map<Long, int[]>> cells, int count) {
    PlayerRef player = adaptPlayer.getPlayer();
    AdaptPlayer.FxPosition position = adaptPlayer.getFxPosition();
    if (player == null || position == null || position.world() == null) {
      return count;
    }

    WorldRef world = position.world();
    players[count] = player;
    xs[count] = position.x();
    ys[count] = position.y();
    zs[count] = position.z();
    Map<Long, int[]> worldCells = cells.computeIfAbsent(world.uid(), w -> new HashMap<>(64));
    addToBucket(worldCells, cellKey(position.x(), position.z()), count);
    return count + 1;
  }

  private static void addToBucket(Map<Long, int[]> worldCells, long key, int index) {
    int[] bucket = worldCells.get(key);
    if (bucket == null) {
      bucket = new int[5];
      bucket[0] = 1;
      bucket[1] = index;
      worldCells.put(key, bucket);
      return;
    }

    int used = bucket[0];
    if (used + 1 >= bucket.length) {
      bucket = Arrays.copyOf(bucket, bucket.length * 2);
      worldCells.put(key, bucket);
    }
    bucket[used + 1] = index;
    bucket[0] = used + 1;
  }

  private static long cellKey(double x, double z) {
    int cx = ((int) Math.floor(x)) >> CELL_SHIFT;
    int cz = ((int) Math.floor(z)) >> CELL_SHIFT;
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }

  static final class Snapshot {
    final long stamp;
    private final PlayerRef[] players;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final Map<UUID, Map<Long, int[]>> cells;
    private final Map<UUID, Integer> dispatchIndices;
    private final AtomicLongArray emissions;
    private final AtomicReferenceArray<FxDispatch.ViewerBatch> dispatches;

    private Snapshot(long stamp, PlayerRef[] players, double[] xs, double[] ys, double[] zs, Map<UUID, Map<Long, int[]>> cells, Map<UUID, Integer> dispatchIndices) {
      this.stamp = stamp;
      this.players = players;
      this.xs = xs;
      this.ys = ys;
      this.zs = zs;
      this.cells = cells;
      this.dispatchIndices = dispatchIndices;
      this.emissions = new AtomicLongArray(players.length);
      this.dispatches = new AtomicReferenceArray<>(players.length);
    }

    int countViewers(WorldRef world, double x, double y, double z, double radius) {
      int[] count = new int[1];
      visit(world, x, y, z, radius, index -> count[0]++);
      return count[0];
    }

    int fillViewerIndices(WorldRef world, double x, double y, double z, double radius, int[] outIndices) {
      int[] filled = new int[1];
      visit(world, x, y, z, radius, index -> {
        int slot = filled[0];
        if (slot < outIndices.length) {
          outIndices[slot] = index;
          filled[0] = slot + 1;
        }
      });
      return filled[0];
    }

    boolean tryEmit(int index) {
      if (index < 0 || index >= emissions.length()) {
        return false;
      }

      int tick = (int) TICK_STAMP.get();
      while (true) {
        long current = emissions.get(index);
        int currentTick = (int) (current >>> 32);
        int count = (int) current;
        if (currentTick == tick && count >= FxBudget.PER_VIEWER_EMISSION_CAP) {
          return false;
        }

        int nextCount = currentTick == tick ? count + 1 : 1;
        long next = (Integer.toUnsignedLong(tick) << 32) | Integer.toUnsignedLong(nextCount);
        if (emissions.compareAndSet(index, current, next)) {
          return true;
        }
      }
    }

    void dispatch(int index, FxDispatch.Emission emission) {
      if (index < 0 || index >= dispatches.length() || emission == null) {
        return;
      }

      FxDispatch.ViewerBatch batch = dispatches.get(index);
      if (batch == null) {
        FxDispatch.ViewerBatch created = new FxDispatch.ViewerBatch(players[index]);
        if (dispatches.compareAndSet(index, null, created)) {
          batch = created;
        } else {
          batch = dispatches.get(index);
        }
      }
      batch.enqueue(emission);
    }

    void dispatch(WorldRef world, double x, double y, double z, double radius, FxDispatch.Emission emission) {
      if (emission == null) {
        return;
      }
      visit(world, x, y, z, radius, index -> dispatch(index, emission));
    }

    boolean dispatch(PlayerRef viewer, FxDispatch.Emission emission) {
      Integer index = dispatchIndices.get(viewer.uuid());
      if (index == null || index < 0) {
        return false;
      }
      dispatch(index, emission);
      return true;
    }

    boolean isKnown(PlayerRef viewer) {
      return dispatchIndices.containsKey(viewer.uuid());
    }

    int indexOf(PlayerRef viewer) {
      return dispatchIndices.getOrDefault(viewer.uuid(), UNINDEXED_VIEWER_INDEX);
    }

    boolean withinRange(int index, double x, double y, double z, double radius) {
      if (index < 0 || index >= players.length) {
        return false;
      }
      double dx = xs[index] - x;
      double dy = ys[index] - y;
      double dz = zs[index] - z;
      return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    boolean shouldFallback(PlayerRef viewer) {
      Integer index = dispatchIndices.get(viewer.uuid());
      return index == null || index == UNINDEXED_VIEWER_INDEX;
    }

    private void visit(WorldRef world, double x, double y, double z, double radius, IntConsumer action) {
      if (world == null || radius <= 0) {
        return;
      }

      Map<Long, int[]> worldCells = cells.get(world.uid());
      if (worldCells == null || worldCells.isEmpty()) {
        return;
      }

      double clamped = Math.min(MAX_CULL_RADIUS, radius);
      double radiusSq = clamped * clamped;
      int cxMin = ((int) Math.floor(x - clamped)) >> CELL_SHIFT;
      int cxMax = ((int) Math.floor(x + clamped)) >> CELL_SHIFT;
      int czMin = ((int) Math.floor(z - clamped)) >> CELL_SHIFT;
      int czMax = ((int) Math.floor(z + clamped)) >> CELL_SHIFT;
      for (int cx = cxMin; cx <= cxMax; cx++) {
        for (int cz = czMin; cz <= czMax; cz++) {
          int[] bucket = worldCells.get((((long) cx) << 32) ^ (cz & 0xFFFFFFFFL));
          if (bucket == null) {
            continue;
          }

          int used = bucket[0];
          for (int k = 1; k <= used; k++) {
            int index = bucket[k];
            double dx = xs[index] - x;
            double dy = ys[index] - y;
            double dz = zs[index] - z;
            if ((dx * dx) + (dy * dy) + (dz * dz) <= radiusSq) {
              action.accept(index);
            }
          }
        }
      }
    }

  }
}
