package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GolemTaskBoard {
  public static final long ACTIVITY_MILLIS = 10_000L;
  public static final long RESERVATION_MILLIS = 5_000L;

  private static final long PURGE_INTERVAL_MILLIS = 5_000L;
  private static final Comparator<Candidate> ORDER = Comparator.comparingInt(Candidate::priority).reversed()
      .thenComparingDouble(Candidate::distanceSquared);

  private final UUID world;
  private final Function<NodeKey, Optional<NodeRecord>> nodes;
  private final Map<NodeKey, NodeTasks> tasks = new ConcurrentHashMap<>();
  private final Map<Long, Set<NodeKey>> chunks = new ConcurrentHashMap<>();
  private final Map<NodeKey, Activity> activity = new ConcurrentHashMap<>();
  private final Map<TaskKey, GolemTask> reservations = new ConcurrentHashMap<>();
  private final AtomicLong nextPurgeAt = new AtomicLong();
  private final AtomicInteger reach = new AtomicInteger();

  public GolemTaskBoard(UUID world, Function<NodeKey, Optional<NodeRecord>> nodes) {
    this.world = Objects.requireNonNull(world);
    this.nodes = Objects.requireNonNull(nodes);
  }

  public UUID world() {
    return world;
  }

  public Optional<NodeRecord> node(NodeKey key) {
    return nodes.apply(key);
  }

  public Offer offer(GolemTask task, int maxOpenTasks) {
    NodeKey key = task.nodeKey();
    if (key == null) {
      throw new IllegalArgumentException("Only node tasks go on a task board, got " + task);
    }
    if (!world.equals(key.world())) {
      throw new IllegalArgumentException("Task " + task + " belongs to world " + key.world() + ", not " + world);
    }
    TaskKey taskKey = task.key();
    while (true) {
      NodeTasks open = tasks.computeIfAbsent(key, ignored -> new NodeTasks());
      synchronized (open) {
        if (tasks.get(key) != open) {
          continue;
        }
        GolemTask existing = open.tasks.get(taskKey);
        if (existing != null && !existing.isClosed()) {
          return Offer.EXISTS;
        }
        if (open.tasks.size() >= Math.max(0, maxOpenTasks)) {
          return Offer.FULL;
        }
        open.tasks.put(taskKey, task);
      }
      chunks.compute(key.chunkKey(), (chunk, keys) -> {
        Set<NodeKey> indexed = keys == null ? ConcurrentHashMap.newKeySet() : keys;
        indexed.add(key);
        return indexed;
      });
      nodes.apply(key).ifPresent(this::widenReach);
      return Offer.ADDED;
    }
  }

  public int openCount(NodeKey node) {
    NodeTasks open = tasks.get(node);
    return open == null ? 0 : open.tasks.size();
  }

  public List<GolemTask> tasks(NodeKey node) {
    NodeTasks open = tasks.get(node);
    return open == null ? List.of() : List.copyOf(open.tasks.values());
  }

  public List<GolemTask> chunkTasks(long chunkKey) {
    Set<NodeKey> keys = chunks.get(chunkKey);
    if (keys == null || keys.isEmpty()) {
      return List.of();
    }
    List<GolemTask> found = new ArrayList<>();
    for (NodeKey key : keys) {
      NodeTasks open = tasks.get(key);
      if (open != null) {
        found.addAll(open.tasks.values());
      }
    }
    return List.copyOf(found);
  }

  public boolean withdraw(GolemTask task) {
    task.close();
    return remove(task);
  }

  public Optional<GolemTask> claim(Seeker seeker, Predicate<GolemTask> skip, long nowMillis, long leaseMillis) {
    for (GolemTask task : candidates(seeker, skip, nowMillis)) {
      if (task.tryClaim(seeker.golem(), nowMillis, leaseMillis)) {
        return Optional.of(task);
      }
    }
    return Optional.empty();
  }

  public List<GolemTask> candidates(Seeker seeker, Predicate<GolemTask> skip, long nowMillis) {
    double radiusSquared = seeker.radius() * seeker.radius();
    int chunkRadius = (int) Math.ceil((seeker.radius() + reach.get() + 1D) / 16D);
    int centerX = (int) Math.floor(seeker.homeX()) >> 4;
    int centerZ = (int) Math.floor(seeker.homeZ()) >> 4;
    List<Candidate> found = new ArrayList<>();
    for (int chunkX = centerX - chunkRadius; chunkX <= centerX + chunkRadius; chunkX++) {
      for (int chunkZ = centerZ - chunkRadius; chunkZ <= centerZ + chunkRadius; chunkZ++) {
        Set<NodeKey> keys = chunks.get(GolemRoster.chunkKey(chunkX, chunkZ));
        if (keys == null) {
          continue;
        }
        for (NodeKey key : keys) {
          collect(key, seeker, skip, nowMillis, radiusSquared, found);
        }
      }
    }
    found.sort(ORDER);
    List<GolemTask> ordered = new ArrayList<>(found.size());
    for (Candidate candidate : found) {
      ordered.add(candidate.task());
    }
    return ordered;
  }

  public boolean renew(GolemTask task, UUID golem, long nowMillis, long leaseMillis) {
    return task.renew(golem, nowMillis, leaseMillis);
  }

  public boolean release(GolemTask task, UUID golem) {
    return task.release(golem);
  }

  public boolean complete(GolemTask task, UUID golem) {
    if (task.isClosed() || !task.holderIs(golem)) {
      return false;
    }
    task.close();
    remove(task);
    return true;
  }

  public boolean reserve(GolemTask task, UUID golem, long nowMillis, long leaseMillis) {
    purgeIfDue(nowMillis);
    boolean[] won = new boolean[1];
    reservations.compute(task.key(), (key, held) -> {
      GolemTask holder = held == null || held.isClosed() ? task : held;
      won[0] = holder.tryClaim(golem, nowMillis, leaseMillis);
      return holder;
    });
    return won[0];
  }

  public boolean keepReservation(GolemTask task, UUID golem, long nowMillis, long leaseMillis) {
    GolemTask held = reservations.get(task.key());
    if (held == null || !held.heldBy(golem, nowMillis)) {
      return false;
    }
    return held.leaseRemaining(nowMillis) >= leaseMillis / 2L || held.renew(golem, nowMillis, leaseMillis);
  }

  public boolean releaseReservation(GolemTask task, UUID golem) {
    boolean[] released = new boolean[1];
    reservations.computeIfPresent(task.key(), (key, held) -> {
      if (!held.release(golem)) {
        return held;
      }
      released[0] = true;
      return null;
    });
    return released[0];
  }

  public boolean reservedByOther(GolemTask task, UUID golem, long nowMillis) {
    if (reservations.isEmpty()) {
      return false;
    }
    GolemTask held = reservations.get(task.key());
    return held != null && held.heldByAnyone(nowMillis) && !held.holderIs(golem);
  }

  public void markWorked(NodeKey node, long nowMillis) {
    activity.computeIfAbsent(node, ignored -> new Activity()).workedAt = nowMillis;
  }

  public void markBlocked(NodeKey node, long nowMillis) {
    activity.computeIfAbsent(node, ignored -> new Activity()).blockedAt = nowMillis;
  }

  public NodeStatus status(NodeKey node, long nowMillis) {
    Activity recent = activity.get(node);
    if (recent != null && recent.blockedAt > 0L && nowMillis - recent.blockedAt < ACTIVITY_MILLIS) {
      return NodeStatus.BLOCKED;
    }
    if (recent != null && recent.workedAt > 0L && nowMillis - recent.workedAt < ACTIVITY_MILLIS) {
      return NodeStatus.WORKING;
    }
    NodeTasks open = tasks.get(node);
    if (open != null) {
      for (GolemTask task : open.tasks.values()) {
        if (task.heldByAnyone(nowMillis)) {
          return NodeStatus.WORKING;
        }
      }
    }
    return NodeStatus.IDLE;
  }

  public void purge(long nowMillis) {
    for (Map.Entry<NodeKey, NodeTasks> entry : tasks.entrySet()) {
      NodeKey key = entry.getKey();
      NodeTasks open = entry.getValue();
      if (nodes.apply(key).isPresent()) {
        for (GolemTask task : open.tasks.values()) {
          task.purgeExpired(nowMillis);
        }
        continue;
      }
      synchronized (open) {
        for (GolemTask task : open.tasks.values()) {
          task.close();
        }
        open.tasks.clear();
        tasks.remove(key, open);
      }
      chunks.computeIfPresent(key.chunkKey(), (chunk, keys) -> {
        keys.remove(key);
        return keys.isEmpty() ? null : keys;
      });
    }
    activity.entrySet().removeIf(entry -> nodes.apply(entry.getKey()).isEmpty());
    for (TaskKey key : reservations.keySet()) {
      reservations.computeIfPresent(key, (ignored, held) -> held.heldByAnyone(nowMillis) ? held : null);
    }
  }

  public void purgeIfDue(long nowMillis) {
    long due = nextPurgeAt.get();
    if (nowMillis >= due && nextPurgeAt.compareAndSet(due, nowMillis + PURGE_INTERVAL_MILLIS)) {
      purge(nowMillis);
    }
  }

  private boolean remove(GolemTask task) {
    NodeKey key = task.nodeKey();
    NodeTasks open = key == null ? null : tasks.get(key);
    if (open == null) {
      return false;
    }
    synchronized (open) {
      return open.tasks.remove(task.key(), task);
    }
  }

  private void collect(NodeKey key, Seeker seeker, Predicate<GolemTask> skip, long nowMillis, double radiusSquared, List<Candidate> found) {
    NodeTasks open = tasks.get(key);
    if (open == null || open.tasks.isEmpty()) {
      return;
    }
    NodeRecord record = nodes.apply(key).orElse(null);
    if (record == null
        || !record.owner().equals(seeker.owner())
        || !GolemTaskMatch.channel(record.channel(), seeker.channels())
        || !GolemTaskMatch.core(record.type(), seeker.cores())) {
      return;
    }
    for (GolemTask task : open.tasks.values()) {
      GolemTarget target = task.target();
      if (!task.openFor(seeker.golem(), nowMillis)
          || !task.allows(seeker.body())
          || target.distanceSquared(seeker.homeX(), seeker.homeY(), seeker.homeZ()) > radiusSquared
          || skip.test(task)) {
        continue;
      }
      found.add(new Candidate(task, record.priority(), target.distanceSquared(seeker.x(), seeker.y(), seeker.z())));
    }
  }

  private void widenReach(NodeRecord record) {
    reach.accumulateAndGet(record.area().horizontalReach() + 1, Math::max);
  }

  public enum Offer {
    ADDED,
    EXISTS,
    FULL
  }

  public record Seeker(UUID golem, UUID owner, List<GolemCoreType> cores, GolemBody body, int channels,
                       double homeX, double homeY, double homeZ, double radius, double x, double y, double z) {
    public Seeker {
      Objects.requireNonNull(golem);
      Objects.requireNonNull(owner);
      cores = List.copyOf(cores);
      Objects.requireNonNull(body);
    }
  }

  private record Candidate(GolemTask task, int priority, double distanceSquared) {
  }

  private static final class NodeTasks {
    private final Map<TaskKey, GolemTask> tasks = new ConcurrentHashMap<>();
  }

  private static final class Activity {
    private volatile long workedAt;
    private volatile long blockedAt;
  }
}
