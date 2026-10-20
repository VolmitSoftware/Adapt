package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AreaSelections {
  public static final long IDLE_MILLIS = 120_000L;
  public static final long AWAY_MILLIS = 2_000L;
  public static final long ECHO_MILLIS = 250L;

  private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
  private final Map<UUID, Long> ended = new ConcurrentHashMap<>();
  private final AtomicLong sessions = new AtomicLong();

  public boolean begin(UUID player, NodeKey node, NodeRecord record, long nowMillis) {
    if (!record.owner().equals(player)) {
      return false;
    }
    selections.put(player, new Selection(sessions.incrementAndGet(), node, null, null, nowMillis, 0L));
    ended.remove(player);
    return true;
  }

  public Optional<Selection> selection(UUID player) {
    return Optional.ofNullable(selections.get(player));
  }

  public boolean swallows(UUID player, long nowMillis) {
    if (selections.containsKey(player)) {
      return true;
    }
    Long endedAt = ended.get(player);
    if (endedAt == null) {
      return false;
    }
    if (nowMillis - endedAt <= ECHO_MILLIS) {
      return true;
    }
    ended.remove(player, endedAt);
    return false;
  }

  public Optional<Selection> corner(UUID player, Corner corner, int x, int y, int z, long nowMillis) {
    Point point = new Point(x, y, z);
    return Optional.ofNullable(selections.computeIfPresent(player, (ignored, current) -> current.with(corner, point, nowMillis)));
  }

  public Applied apply(UUID player, int maxRadius, long nowMillis) {
    Selection current = selections.computeIfPresent(player, (ignored, found) -> found.touched(nowMillis));
    if (current == null) {
      return new Applied(Outcome.NOT_SELECTING, null, null, null);
    }
    if (!current.complete()) {
      return new Applied(Outcome.INCOMPLETE, current.node(), null, null);
    }
    NodeCuboid area = current.area();
    NodeCuboid.Limit limit = area.check(maxRadius);
    if (!limit.fits()) {
      return new Applied(Outcome.REJECTED, current.node(), area, limit);
    }
    end(player, nowMillis);
    return new Applied(Outcome.READY, current.node(), area, limit);
  }

  public Optional<Selection> end(UUID player, long nowMillis) {
    Selection removed = selections.remove(player);
    if (removed != null) {
      ended.put(player, nowMillis);
    }
    return Optional.ofNullable(removed);
  }

  public void forget(UUID player) {
    selections.remove(player);
    ended.remove(player);
  }

  public Exit watch(UUID player, Watch facts, long nowMillis) {
    Selection current = selections.get(player);
    if (current == null) {
      return Exit.NONE;
    }
    Exit exit = exit(current, facts, nowMillis);
    if (exit != Exit.NONE) {
      end(player, nowMillis);
      return exit;
    }
    long awaySince = facts.holdingCrook() ? 0L : current.awaySince() == 0L ? nowMillis : current.awaySince();
    if (awaySince != current.awaySince()) {
      selections.replace(player, current, current.away(awaySince));
    }
    return Exit.NONE;
  }

  private static Exit exit(Selection current, Watch facts, long nowMillis) {
    if (!facts.nodePresent()) {
      return Exit.NODE_GONE;
    }
    if (!current.node().world().equals(facts.world())) {
      return Exit.WORLD_CHANGED;
    }
    if (nowMillis - current.touchedAt() >= IDLE_MILLIS) {
      return Exit.TIMED_OUT;
    }
    if (!facts.holdingCrook() && current.awaySince() != 0L && nowMillis - current.awaySince() >= AWAY_MILLIS) {
      return Exit.PUT_AWAY;
    }
    return Exit.NONE;
  }

  public enum Corner {
    FIRST,
    SECOND
  }

  public enum Outcome {
    NOT_SELECTING,
    INCOMPLETE,
    REJECTED,
    READY
  }

  public enum Exit {
    NONE,
    TIMED_OUT,
    PUT_AWAY,
    NODE_GONE,
    WORLD_CHANGED
  }

  public record Watch(UUID world, boolean holdingCrook, boolean nodePresent) {
  }

  public record Point(int x, int y, int z) {
  }

  public record Applied(Outcome outcome, NodeKey node, NodeCuboid area, NodeCuboid.Limit limit) {
  }

  public record Selection(long session, NodeKey node, Point first, Point second, long touchedAt, long awaySince) {
    public boolean complete() {
      return first != null && second != null;
    }

    public NodeCuboid area() {
      int[] center = NodeArea.center(node);
      return new NodeCuboid(Math.min(first.x(), second.x()) - center[0], Math.min(first.y(), second.y()) - center[1],
          Math.min(first.z(), second.z()) - center[2], Math.max(first.x(), second.x()) - center[0],
          Math.max(first.y(), second.y()) - center[1], Math.max(first.z(), second.z()) - center[2]);
    }

    private Selection with(Corner corner, Point point, long nowMillis) {
      return corner == Corner.FIRST
          ? new Selection(session, node, point, second, nowMillis, awaySince)
          : new Selection(session, node, first, point, nowMillis, awaySince);
    }

    private Selection touched(long nowMillis) {
      return new Selection(session, node, first, second, nowMillis, awaySince);
    }

    private Selection away(long since) {
      return new Selection(session, node, first, second, touchedAt, since);
    }
  }
}
