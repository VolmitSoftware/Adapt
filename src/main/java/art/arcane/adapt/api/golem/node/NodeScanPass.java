package art.arcane.adapt.api.golem.node;

import org.bukkit.World;

import java.util.function.BooleanSupplier;

public record NodeScanPass(World world, NodeKey key, NodeRecord record, NodeScanCursor.Window window, int maxOpenTasks,
                           NodeDiscovery discovery, BooleanSupplier current) {
  public NodeScanPass(World world, NodeKey key, NodeRecord record, NodeScanCursor.Window window, int maxOpenTasks) {
    this(world, key, record, window, maxOpenTasks, null, () -> true);
  }
}
