package art.arcane.adapt.api.golem.node;

import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

final class NodeDiscovery {
  static final int MAX_CANDIDATES = 4096;
  static final int MAX_DIRTY = 256;

  private final NodeScanCursor cursor = new NodeScanCursor();
  private final BitSet known = new BitSet(MAX_CANDIDATES);
  private final BitSet negative = new BitSet(MAX_CANDIDATES);
  private final Set<Integer> dirty = new LinkedHashSet<>();
  private int knownCursor;
  private long emptyQueryUntil;
  private long surveyCriteria;
  private boolean classifiedCriteria;

  synchronized NodeScanCursor.Window next(int requested, int volume) {
    int budget = Math.min(Math.max(1, requested), volume);
    BitSet selected = new BitSet(volume);
    int[] positions = new int[budget];
    int count = 0;
    boolean classified = known.cardinality() + negative.cardinality() >= volume;
    int reconcile = Math.max(1, classified ? budget / 4 : budget * 3 / 4);
    int urgentLimit = (budget - reconcile + 1) / 2;
    Iterator<Integer> urgent = dirty.iterator();
    while (urgent.hasNext() && count < urgentLimit) {
      int index = urgent.next();
      urgent.remove();
      if (index >= 0 && index < volume) {
        positions[count++] = index;
        selected.set(index);
      }
    }
    int available = known.cardinality();
    for (int visited = 0; visited < available && count < budget - reconcile; visited++) {
      int index = known.nextSetBit(knownCursor);
      if (index < 0) {
        index = known.nextSetBit(0);
      }
      if (index < 0) {
        break;
      }
      knownCursor = index + 1;
      if (index < volume && !selected.get(index)) {
        positions[count++] = index;
        selected.set(index);
      }
    }
    int remaining = classified ? Math.min(reconcile, budget - count) : budget - count;
    int targetCount = count + remaining;
    int traversed = 0;
    while (count < targetCount && traversed < volume) {
      NodeScanCursor.Window window = cursor.next(Math.min(targetCount - count, volume - traversed), volume);
      traversed += window.count();
      for (int index = window.start(); index < window.start() + window.count(); index++) {
        if (!selected.get(index)) {
          positions[count++] = index;
          selected.set(index);
        }
      }
    }
    return new NodeScanCursor.Window(positions, count);
  }

  synchronized void remember(int index, boolean candidate) {
    if (index < 0 || index >= MAX_CANDIDATES) {
      return;
    }
    known.set(index, candidate);
    negative.set(index, !candidate);
  }

  synchronized void dirty(int index) {
    if (index >= 0 && index < MAX_CANDIDATES && dirty.size() < MAX_DIRTY) {
      dirty.add(index);
      negative.clear(index);
    }
  }

  synchronized void surveyCriteria(long next) {
    if (classifiedCriteria && surveyCriteria != next) {
      negative.clear();
    }
    surveyCriteria = next;
    classifiedCriteria = true;
  }

  synchronized boolean queryDue(long nowMillis) {
    return nowMillis >= emptyQueryUntil;
  }

  synchronized void queryResult(boolean found, long nowMillis) {
    emptyQueryUntil = found ? 0L : nowMillis + 10_000L;
  }

  synchronized void wakeQuery() {
    emptyQueryUntil = 0L;
  }

  synchronized int[] knownIndices() {
    return known.stream().toArray();
  }

  synchronized int remembered() {
    return known.cardinality();
  }

  synchronized int pending() {
    return dirty.size();
  }
}
