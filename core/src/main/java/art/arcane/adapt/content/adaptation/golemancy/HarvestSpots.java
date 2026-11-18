package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

final class HarvestSpots {
  static final int MAX_MARKS_PER_NODE = 128;
  static final long STALE_MILLIS = 60_000L;

  private final Map<NodeKey, NodeMarks> nodes = new ConcurrentHashMap<>();

  void scanned(NodeKey key, NodeRecord record, long nowMillis) {
    NodeMarks marks = nodes.computeIfAbsent(key, ignored -> new NodeMarks());
    NodeCuboid area = record.area();
    synchronized (marks) {
      if (!area.equals(marks.area)) {
        marks.spots.clear();
        marks.area = area;
      }
    }
    marks.scannedAt = nowMillis;
  }

  void record(NodeKey key, int x, int y, int z, HarvesterRules.Spot spot) {
    NodeMarks marks = nodes.get(key);
    if (marks == null) {
      return;
    }
    boolean keep = spot == HarvesterRules.Spot.TEND || spot == HarvesterRules.Spot.PLANT;
    Position at = new Position(x, y, z);
    synchronized (marks) {
      if (!keep) {
        marks.spots.remove(at);
        return;
      }
      if (marks.spots.size() >= MAX_MARKS_PER_NODE && !marks.spots.containsKey(at)) {
        marks.spots.remove(marks.spots.keySet().iterator().next());
      }
      marks.spots.put(at, spot);
    }
  }

  void drop(NodeKey key, int x, int y, int z) {
    NodeMarks marks = nodes.get(key);
    if (marks != null) {
      synchronized (marks) {
        marks.spots.remove(new Position(x, y, z));
      }
    }
  }

  List<Mark> of(NodeKey key) {
    NodeMarks marks = nodes.get(key);
    if (marks == null) {
      return List.of();
    }
    synchronized (marks) {
      List<Mark> found = new ArrayList<>(marks.spots.size());
      for (Map.Entry<Position, HarvesterRules.Spot> entry : marks.spots.entrySet()) {
        Position at = entry.getKey();
        found.add(new Mark(entry.getValue(), at.x(), at.y(), at.z()));
      }
      return found;
    }
  }

  boolean pending() {
    return !nodes.isEmpty();
  }

  void sweep(long nowMillis) {
    nodes.values().removeIf(marks -> nowMillis - marks.scannedAt >= STALE_MILLIS);
  }

  void forget() {
    nodes.clear();
  }

  record Mark(HarvesterRules.Spot kind, int x, int y, int z) {
  }

  private record Position(int x, int y, int z) {
  }

  private static final class NodeMarks {
    private final Map<Position, HarvesterRules.Spot> spots = new LinkedHashMap<>();
    private volatile long scannedAt;
    private volatile NodeCuboid area;
  }
}
