package art.arcane.adapt.api.golem.node;

import org.bukkit.World;

public record NodeScanPass(World world, NodeKey key, NodeRecord record, NodeScanCursor.Window window, int maxOpenTasks) {
}
