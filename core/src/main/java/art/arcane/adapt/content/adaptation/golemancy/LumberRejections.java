package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LumberRejections {
  static final long REJECT_MILLIS = 60_000L;

  private final Map<NodeKey, Map<Long, Long>> rejected = new ConcurrentHashMap<>();

  boolean rejected(NodeKey node, int x, int y, int z, long nowMillis) {
    Map<Long, Long> bases = rejected.get(node);
    if (bases == null) {
      return false;
    }
    Long until = bases.get(NodeContainers.blockKey(x, y, z));
    return until != null && nowMillis < until;
  }

  void reject(NodeKey node, int x, int y, int z, long nowMillis) {
    rejected.computeIfAbsent(node, ignored -> new ConcurrentHashMap<>(4)).put(NodeContainers.blockKey(x, y, z), nowMillis + REJECT_MILLIS);
  }

  void sweep(long nowMillis) {
    for (Map<Long, Long> bases : rejected.values()) {
      bases.values().removeIf(until -> nowMillis >= until);
    }
    rejected.values().removeIf(Map::isEmpty);
  }

  boolean pending() {
    return !rejected.isEmpty();
  }

  void forget() {
    rejected.clear();
  }
}
