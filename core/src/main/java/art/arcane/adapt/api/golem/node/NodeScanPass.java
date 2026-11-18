package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.platform.world.WorldRef;

import java.util.function.BooleanSupplier;

public record NodeScanPass(WorldRef world, NodeKey key, NodeRecord record, NodeScanCursor.Window window, int maxOpenTasks,
                           NodeDiscovery discovery, BooleanSupplier current) {
  public NodeScanPass(WorldRef world, NodeKey key, NodeRecord record, NodeScanCursor.Window window, int maxOpenTasks) {
    this(world, key, record, window, maxOpenTasks, null, () -> true);
  }
}
