package art.arcane.adapt.api.golem.node;

import java.util.BitSet;

public final class NodeDiscoveryBenchmark {
  private static final int VOLUME = 4096;
  private static final int BUDGET = 64;
  private static final int REPEAT_PASSES = 100;

  public static void main(String[] args) {
    reportIdle("empty-air", false);
    reportIdle("irrelevant-solid-rock", false);
    reportIdle("farm-128-known-crops", true);
    reportRediscovery();
    reportFairness();
    reportEmptyQueries();
  }

  private static void reportIdle(String workload, boolean farm) {
    NodeDiscovery discovery = new NodeDiscovery();
    BitSet classified = new BitSet(VOLUME);
    int warmupReads = 0;
    while (classified.cardinality() < VOLUME) {
      for (int index : discovery.next(BUDGET, VOLUME).indices()) {
        discovery.remember(index, farm && index < 128);
        classified.set(index);
        warmupReads++;
      }
    }
    int reads = 0;
    for (int pass = 0; pass < REPEAT_PASSES; pass++) {
      for (int index : discovery.next(BUDGET, VOLUME).indices()) {
        discovery.remember(index, farm && index < 128);
        reads++;
      }
    }
    System.out.printf("%s volume=%d budget=%d warmupPositionReads=%d repeatPasses=%d baselineRepeatPositionReads=%d cachedRepeatPositionReads=%d%n",
        workload, VOLUME, BUDGET, warmupReads, REPEAT_PASSES, REPEAT_PASSES * BUDGET, reads);
  }

  private static void reportRediscovery() {
    int target = 10;
    NodeScanCursor baseline = new NodeScanCursor();
    baseline.next(BUDGET, VOLUME);
    int baselinePasses = 0;
    while (++baselinePasses <= 256) {
      NodeScanCursor.Window window = baseline.next(BUDGET, VOLUME);
      if (target >= window.start() && target < window.start() + window.count()) {
        break;
      }
    }
    NodeDiscovery deferred = new NodeDiscovery();
    deferred.next(BUDGET, VOLUME);
    deferred.remember(target, true);
    NodeDiscovery changed = new NodeDiscovery();
    for (int index = 0; index < VOLUME; index++) {
      changed.remember(index, false);
    }
    changed.dirty(target);
    System.out.printf("deferred-and-event-change baselineRediscoveryPasses=%d deferredPasses=%d dirtyPasses=%d intervalTicks=40%n",
        baselinePasses, until(deferred, target), until(changed, target));
  }

  private static void reportFairness() {
    NodeDiscovery discovery = new NodeDiscovery();
    BitSet seen = new BitSet(VOLUME);
    for (int index = 0; index < VOLUME; index++) {
      discovery.remember(index, true);
    }
    int passes = 0;
    int maxPositions = 0;
    while (seen.cardinality() < VOLUME && ++passes <= 256) {
      for (int index = 0; index < 256; index++) {
        discovery.dirty(index);
      }
      int[] positions = discovery.next(BUDGET, VOLUME).indices();
      maxPositions = Math.max(maxPositions, positions.length);
      for (int index : positions) {
        seen.set(index);
      }
    }
    System.out.printf("dirty-flood known=4096 hintsPerPass=256 completeCoveragePasses=%d maxPositionReadsPerPass=%d%n", passes, maxPositions);
    NodeDiscovery silent = new NodeDiscovery();
    for (int index = 0; index < VOLUME; index++) {
      silent.remember(index, false);
    }
    seen.clear();
    for (int pass = 0; pass < 256; pass++) {
      for (int index : silent.next(BUDGET, VOLUME).indices()) {
        seen.set(index);
      }
    }
    System.out.printf("silent-plugin-mutation authoritativeCoverageAfter256Passes=%d intervalTicks=40 maximumReconcileSeconds=512%n", seen.cardinality());
  }

  private static void reportEmptyQueries() {
    NodeDiscovery collection = new NodeDiscovery();
    NodeDiscovery pasture = new NodeDiscovery();
    int collectionQueries = 0;
    int pastureQueries = 0;
    for (long now = 0; now < 100_000; now += 1_000) {
      if (now % 2_000 == 0 && collection.queryDue(now)) {
        collection.queryResult(false, now);
        collectionQueries++;
      }
      if (pasture.queryDue(now)) {
        pasture.queryResult(false, now);
        pastureQueries++;
      }
    }
    System.out.printf("empty-entity-queries durationSeconds=100 collectionBaseline=50 collectionCached=%d pastureBaseline=100 pastureCached=%d%n",
        collectionQueries, pastureQueries);
  }

  private static int until(NodeDiscovery discovery, int target) {
    for (int pass = 1; pass <= 256; pass++) {
      for (int index : discovery.next(BUDGET, VOLUME).indices()) {
        if (index == target) {
          return pass;
        }
      }
    }
    throw new IllegalStateException("Target was not revisited");
  }
}
