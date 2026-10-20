package art.arcane.adapt.api.golem.node;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

class NodeDiscoveryTest {
  @Test
  void deferredCandidatesReturnWithoutWaitingForTheFullCursor() {
    NodeDiscovery discovery = new NodeDiscovery();
    discovery.next(64, 4096);
    discovery.remember(3400, true);

    assertThat(discovery.next(64, 4096).indices()).contains(3400);
    assertThat(discovery.next(64, 4096).indices()).contains(3400);

    discovery.remember(3400, false);
    assertThat(discovery.remembered()).isZero();
  }

  @Test
  void dirtyAndDenseKnownWorkCannotStarveReconciliation() {
    NodeDiscovery discovery = new NodeDiscovery();
    for (int index = 0; index < 4096; index++) {
      discovery.remember(index, true);
    }
    BitSet seen = new BitSet(4096);
    for (int scan = 0; scan < 256; scan++) {
      for (int index = 0; index < 256; index++) {
        discovery.dirty(index);
      }
      int[] indices = discovery.next(64, 4096).indices();
      assertThat(indices).hasSizeLessThanOrEqualTo(64);
      for (int index : indices) {
        seen.set(index);
      }
    }

    assertThat(seen.cardinality()).isEqualTo(4096);
  }

  @Test
  void memoryAndWorkStayBoundedWhenHintsFlood() {
    NodeDiscovery discovery = new NodeDiscovery();
    for (int index = -100; index < 100_000; index++) {
      discovery.dirty(index);
      discovery.remember(index, true);
    }

    assertThat(discovery.pending()).isEqualTo(NodeDiscovery.MAX_DIRTY);
    assertThat(discovery.remembered()).isEqualTo(NodeDiscovery.MAX_CANDIDATES);
    assertThat(discovery.next(64, 4096).indices()).hasSizeLessThanOrEqualTo(64);
  }

  @Test
  void knownCandidatesRotateFairlyWhileTheFullCursorRuns() {
    NodeDiscovery discovery = new NodeDiscovery();
    for (int index = 3000; index < 3200; index++) {
      discovery.remember(index, true);
    }
    BitSet seen = new BitSet(4096);
    for (int scan = 0; scan < 13; scan++) {
      for (int index : discovery.next(64, 4096).indices()) {
        seen.set(index);
      }
    }

    assertThat(seen.get(3000, 3200).cardinality()).isEqualTo(200);
  }

  @Test
  void classifiedEmptyTerrainUsesOnlyTheReservedReconciliationReads() {
    NodeDiscovery discovery = new NodeDiscovery();
    for (int index = 0; index < 4096; index++) {
      discovery.remember(index, false);
    }

    for (int scan = 0; scan < 64; scan++) {
      assertThat(discovery.next(64, 4096).indices()).hasSize(16);
    }
  }

  @Test
  void dirtyHintsWakeNegativePositionsAndSilentChangesStillReconcile() {
    NodeDiscovery discovery = new NodeDiscovery();
    for (int index = 0; index < 4096; index++) {
      discovery.remember(index, false);
    }
    discovery.dirty(3500);
    assertThat(discovery.next(64, 4096).indices()).contains(3500);
    discovery.remember(3500, false);
    BitSet reconciled = new BitSet(4096);
    for (int scan = 0; scan < 256; scan++) {
      int[] indices = discovery.next(64, 4096).indices();
      assertThat(indices).hasSizeLessThanOrEqualTo(16);
      for (int index : indices) {
        reconciled.set(index);
      }
    }

    assertThat(reconciled.cardinality()).isEqualTo(4096);
  }

  @Test
  void usefulKnownFarmTargetsKeepTheirFullBudget() {
    NodeDiscovery discovery = new NodeDiscovery();
    for (int index = 0; index < 4096; index++) {
      discovery.remember(index, index < 128);
    }

    assertThat(discovery.next(64, 4096).indices()).hasSize(64);
  }

  @Test
  void emptyEntityQueriesBackOffForTenSecondsAndSpawnHintsWakeThem() {
    NodeDiscovery discovery = new NodeDiscovery();
    assertThat(discovery.queryDue(1000)).isTrue();
    discovery.queryResult(false, 1000);
    assertThat(discovery.queryDue(10_999)).isFalse();
    assertThat(discovery.queryDue(11_000)).isTrue();
    discovery.queryResult(false, 11_000);
    discovery.wakeQuery();
    assertThat(discovery.queryDue(11_001)).isTrue();
    discovery.queryResult(true, 11_001);
    assertThat(discovery.queryDue(11_002)).isTrue();
  }

  @Test
  void changedSurveyCriteriaClearNegativeClassificationsAndPreserveKnownPositions() {
    NodeDiscovery discovery = new NodeDiscovery();
    discovery.surveyCriteria(4);
    for (int index = 0; index < 4096; index++) {
      discovery.remember(index, index == 3400);
    }
    assertThat(discovery.next(64, 4096).indices()).hasSize(17);
    discovery.surveyCriteria(4);
    assertThat(discovery.next(64, 4096).indices()).hasSize(17);

    discovery.surveyCriteria(5);

    assertThat(discovery.remembered()).isEqualTo(1);
    assertThat(discovery.next(64, 4096).indices()).hasSize(64).contains(3400);
  }

  @Test
  void aSinglePositionBudgetStillReconcilesEveryPosition() {
    NodeDiscovery discovery = new NodeDiscovery();
    discovery.remember(1, true);
    discovery.dirty(1);
    BitSet seen = new BitSet(16);
    for (int scan = 0; scan < 16; scan++) {
      for (int index : discovery.next(1, 16).indices()) {
        seen.set(index);
      }
    }

    assertThat(seen.cardinality()).isEqualTo(16);
  }
}
