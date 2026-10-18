package art.arcane.adapt.api.golem.node;

import java.util.Objects;

public record NodeSettings(int maxNodesPerChunk, int scanIntervalTicks, int blocksPerScan, int maxOpenTasksPerNode, NodeVisibility visibility) {
  public static final int MAX_NODES_PER_CHUNK = 256;
  public static final int MIN_SCAN_INTERVAL_TICKS = 5;
  public static final int MAX_SCAN_INTERVAL_TICKS = 1200;
  public static final int MAX_BLOCKS_PER_SCAN = 4096;
  public static final int MAX_OPEN_TASKS_PER_NODE = 256;
  private static final int DEFAULT_MAX_NODES_PER_CHUNK = 16;
  private static final int DEFAULT_SCAN_INTERVAL_TICKS = 40;
  private static final int DEFAULT_BLOCKS_PER_SCAN = 64;
  private static final int DEFAULT_MAX_OPEN_TASKS_PER_NODE = 16;

  public NodeSettings {
    maxNodesPerChunk = Math.max(0, Math.min(MAX_NODES_PER_CHUNK, maxNodesPerChunk));
    scanIntervalTicks = Math.max(MIN_SCAN_INTERVAL_TICKS, Math.min(MAX_SCAN_INTERVAL_TICKS, scanIntervalTicks));
    blocksPerScan = Math.max(1, Math.min(MAX_BLOCKS_PER_SCAN, blocksPerScan));
    maxOpenTasksPerNode = Math.max(1, Math.min(MAX_OPEN_TASKS_PER_NODE, maxOpenTasksPerNode));
    visibility = Objects.requireNonNull(visibility);
  }

  public static NodeSettings defaults() {
    return new NodeSettings(DEFAULT_MAX_NODES_PER_CHUNK, DEFAULT_SCAN_INTERVAL_TICKS, DEFAULT_BLOCKS_PER_SCAN,
        DEFAULT_MAX_OPEN_TASKS_PER_NODE, NodeVisibility.OWNER);
  }
}
