package art.arcane.adapt.api.golem.node;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeSettingsTest {
  @Test
  void defaultsMatchThePlannedBudget() {
    NodeSettings defaults = NodeSettings.defaults();

    assertThat(defaults.maxNodesPerChunk()).isEqualTo(16);
    assertThat(defaults.scanIntervalTicks()).isEqualTo(40);
    assertThat(defaults.blocksPerScan()).isEqualTo(64);
    assertThat(defaults.maxOpenTasksPerNode()).isEqualTo(16);
    assertThat(defaults.visibility()).isEqualTo(NodeVisibility.OWNER);
  }

  @Test
  void valuesAreClampedIntoSaneRanges() {
    NodeSettings low = new NodeSettings(-1, 0, 0, -5, NodeVisibility.OWNER);
    NodeSettings high = new NodeSettings(10_000, 100_000, 100_000, 10_000, NodeVisibility.ANY_GOLEMANCER);

    assertThat(low.maxNodesPerChunk()).isZero();
    assertThat(low.scanIntervalTicks()).isEqualTo(NodeSettings.MIN_SCAN_INTERVAL_TICKS);
    assertThat(low.blocksPerScan()).isEqualTo(1);
    assertThat(low.maxOpenTasksPerNode()).isEqualTo(1);
    assertThat(high.maxNodesPerChunk()).isEqualTo(NodeSettings.MAX_NODES_PER_CHUNK);
    assertThat(high.scanIntervalTicks()).isEqualTo(NodeSettings.MAX_SCAN_INTERVAL_TICKS);
    assertThat(high.blocksPerScan()).isEqualTo(NodeSettings.MAX_BLOCKS_PER_SCAN);
    assertThat(high.maxOpenTasksPerNode()).isEqualTo(NodeSettings.MAX_OPEN_TASKS_PER_NODE);
  }

  @Test
  void visibilityNamesParseCaseInsensitivelyAndFallBackToOwner() {
    assertThat(NodeVisibility.parse("any_golemancer")).isEqualTo(NodeVisibility.ANY_GOLEMANCER);
    assertThat(NodeVisibility.parse(" OWNER ")).isEqualTo(NodeVisibility.OWNER);
    assertThat(NodeVisibility.parse("everyone")).isEqualTo(NodeVisibility.OWNER);
    assertThat(NodeVisibility.parse(null)).isEqualTo(NodeVisibility.OWNER);
  }
}
