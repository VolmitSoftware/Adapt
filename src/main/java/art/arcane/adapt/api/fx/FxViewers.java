package art.arcane.adapt.api.fx;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class FxViewers {
  public static final double DEFAULT_CULL_RADIUS = 24.0D;
  public static final double MAX_CULL_RADIUS = 48.0D;
  private static final int CELL_SHIFT = 5;
  private static final Snapshot EMPTY = new Snapshot(Long.MIN_VALUE, new Player[0], new double[0], new double[0], new double[0], Map.of());
  private static final Object BUILD_LOCK = new Object();
  private static final AtomicLong TICK_STAMP = new AtomicLong();
  private static volatile Snapshot snapshot = EMPTY;

  private FxViewers() {
  }

  public static void forEach(World world, double x, double y, double z, double radius, Consumer<Player> action) {
    current().forEach(world, x, y, z, radius, action);
  }

  static void bumpTick() {
    TICK_STAMP.incrementAndGet();
  }

  static Snapshot current() {
    long stamp = TICK_STAMP.get();
    Snapshot current = snapshot;
    if (current.stamp == stamp) {
      return current;
    }

    synchronized (BUILD_LOCK) {
      current = snapshot;
      stamp = TICK_STAMP.get();
      if (current.stamp == stamp) {
        return current;
      }

      Snapshot built = build(stamp);
      snapshot = built;
      return built;
    }
  }

  private static Snapshot build(long stamp) {
    AdaptServer server = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    List<AdaptPlayer> adaptPlayers = server == null ? null : server.getOnlineAdaptPlayerSnapshot();
    int capacity = adaptPlayers == null ? Bukkit.getOnlinePlayers().size() : adaptPlayers.size();
    Player[] players = new Player[capacity];
    double[] xs = new double[capacity];
    double[] ys = new double[capacity];
    double[] zs = new double[capacity];
    Map<World, Map<Long, int[]>> cells = new HashMap<>(4);
    Location scratch = new Location(null, 0, 0, 0);
    int count = 0;

    if (adaptPlayers != null) {
      for (int i = 0; i < adaptPlayers.size() && count < capacity; i++) {
        AdaptPlayer adaptPlayer = adaptPlayers.get(i);
        if (adaptPlayer == null || adaptPlayer.getData() == null || !adaptPlayer.getData().isEffectsEnabled()) {
          continue;
        }
        count = index(adaptPlayer.getPlayer(), players, xs, ys, zs, cells, scratch, count);
      }
    } else {
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (count >= capacity) {
          break;
        }
        count = index(player, players, xs, ys, zs, cells, scratch, count);
      }
    }

    if (count < capacity) {
      players = Arrays.copyOf(players, count);
      xs = Arrays.copyOf(xs, count);
      ys = Arrays.copyOf(ys, count);
      zs = Arrays.copyOf(zs, count);
    }

    return new Snapshot(stamp, players, xs, ys, zs, cells);
  }

  private static int index(Player player, Player[] players, double[] xs, double[] ys, double[] zs, Map<World, Map<Long, int[]>> cells, Location scratch, int count) {
    if (player == null || !player.isOnline()) {
      return count;
    }

    World world = player.getWorld();
    if (world == null) {
      return count;
    }

    Location location = player.getLocation(scratch);
    players[count] = player;
    xs[count] = location.getX();
    ys[count] = location.getY();
    zs[count] = location.getZ();
    Map<Long, int[]> worldCells = cells.computeIfAbsent(world, w -> new HashMap<>(64));
    addToBucket(worldCells, cellKey(location.getX(), location.getZ()), count);
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
    private final Player[] players;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final Map<World, Map<Long, int[]>> cells;
    private final AtomicIntegerArray emissions;

    private Snapshot(long stamp, Player[] players, double[] xs, double[] ys, double[] zs, Map<World, Map<Long, int[]>> cells) {
      this.stamp = stamp;
      this.players = players;
      this.xs = xs;
      this.ys = ys;
      this.zs = zs;
      this.cells = cells;
      this.emissions = new AtomicIntegerArray(players.length);
    }

    void forEach(World world, double x, double y, double z, double radius, Consumer<Player> action) {
      visit(world, x, y, z, radius, index -> action.accept(players[index]));
    }

    int countViewers(World world, double x, double y, double z, double radius) {
      int[] count = new int[1];
      visit(world, x, y, z, radius, index -> count[0]++);
      return count[0];
    }

    int fillViewers(World world, double x, double y, double z, double radius, Player[] outPlayers, int[] outIndices) {
      int[] filled = new int[1];
      visit(world, x, y, z, radius, index -> {
        int slot = filled[0];
        if (slot < outPlayers.length) {
          outPlayers[slot] = players[index];
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
      return emissions.getAndIncrement(index) < FxBudget.PER_VIEWER_EMISSION_CAP;
    }

    private void visit(World world, double x, double y, double z, double radius, IntConsumer action) {
      if (world == null || radius <= 0) {
        return;
      }

      Map<Long, int[]> worldCells = cells.get(world);
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
