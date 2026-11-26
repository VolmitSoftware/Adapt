package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

/**
 * Instant Recall's trail: a rolling record of where each player stood and how they fared, sampled on movement, from
 * which a moment some seconds back can be found and restored.
 */
public final class ChronosRecallHistory {
  private final Map<UUID, Deque<Snapshot>> snapshots;
  private final Map<UUID, Long> lastSnapshot;

  public record Snapshot(long timestamp,
                         String worldKey,
                         double x,
                         double y,
                         double z,
                         float yaw,
                         float pitch,
                         double health,
                         int foodLevel,
                         float saturation,
                         float exhaustion,
                         int fireTicks) {
  }

  /** Both maps come from the owner's player state so they are cleared with the player. */
  public ChronosRecallHistory(Map<UUID, Deque<Snapshot>> snapshots, Map<UUID, Long> lastSnapshot) {
    this.snapshots = snapshots;
    this.lastSnapshot = lastSnapshot;
  }

  public static Snapshot snapshotFromPlayer(PlayerRef p, long now) {
    return new Snapshot(now,
        worldKey(p.world()),
        p.location().x(),
        p.location().y(),
        p.location().z(),
        p.location().yaw(),
        p.location().pitch(),
        p.health(),
        p.foodLevel(),
        p.saturation(),
        p.exhaustion(),
        p.fireTicks());
  }

  private static Snapshot snapshotFromLocation(PlayerRef p, Loc location, long now) {
    WorldRef world = location.world();
    if (world == null) {
      world = p.world();
    }

    return new Snapshot(now,
        worldKey(world),
        location.x(),
        location.y(),
        location.z(),
        location.yaw(),
        location.pitch(),
        p.health(),
        p.foodLevel(),
        p.saturation(),
        p.exhaustion(),
        p.fireTicks());
  }

  /** Starts the trail over at {@code location}, for a player who moved without walking. */
  public void reset(PlayerRef p, Loc location) {
    if (location == null) {
      return;
    }

    long now = M.ms();
    UUID id = p.uuid();
    Deque<Snapshot> queue = snapshots.computeIfAbsent(id, unused -> new ArrayDeque<>());
    queue.clear();
    queue.addLast(snapshotFromLocation(p, location, now));
    lastSnapshot.put(id, now);
  }

  /** Samples the player at most once per interval, dropping samples older than {@code maxAgeMillis}. */
  public void capture(PlayerRef p, long intervalMillis, long maxAgeMillis) {
    long now = M.ms();
    UUID id = p.uuid();
    long last = lastSnapshot.getOrDefault(id, 0L);
    if (now - last < intervalMillis) {
      return;
    }

    lastSnapshot.put(id, now);
    Deque<Snapshot> queue = snapshots.computeIfAbsent(id, k -> new ArrayDeque<>());
    queue.addLast(snapshotFromPlayer(p, now));

    while (!queue.isEmpty() && now - queue.getFirst().timestamp() > maxAgeMillis) {
      queue.removeFirst();
    }
  }

  /** The newest sample at least {@code rewindMillis} old, the oldest one kept, or null without a trail. */
  public Snapshot find(PlayerRef p, long rewindMillis) {
    Deque<Snapshot> queue = snapshots.get(p.uuid());
    if (queue == null || queue.isEmpty()) {
      return null;
    }

    long target = M.ms() - rewindMillis;
    Snapshot fallback = queue.getFirst();

    for (Snapshot s : queue) {
      if (s.timestamp() <= target) {
        fallback = s;
      } else {
        break;
      }
    }

    return fallback;
  }

  public Deque<Snapshot> trail(UUID id) {
    return snapshots.get(id);
  }

  public void forget(UUID id) {
    snapshots.remove(id);
    lastSnapshot.remove(id);
  }

  public void clear() {
    snapshots.clear();
    lastSnapshot.clear();
  }

  public static Loc toLocation(Snapshot snapshot, WorldRef fallback) {
    WorldRef resolved = AdaptCore.platform().host().world(Id.parse(snapshot.worldKey()));
    WorldRef world = resolved == null ? fallback : resolved;
    return new Loc(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
  }

  /** Puts health, hunger, fire and motion back as they were at the snapshot. */
  public static void applySnapshotState(PlayerRef p, Snapshot snapshot) {
    double maxHealth = p.maxHealth();
    p.setHealth(Math.max(1, Math.min(maxHealth, snapshot.health())));
    p.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel())));
    p.setSaturation(Math.max(0, snapshot.saturation()));
    p.setExhaustion(Math.max(0, snapshot.exhaustion()));
    p.setFireTicks(Math.max(0, snapshot.fireTicks()));
    p.setFallDistance(0);
    p.setVelocity(Vec3.ZERO);
  }

  private static String worldKey(WorldRef world) {
    return world.key().toString();
  }
}
