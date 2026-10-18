package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.entity.Player;

public record NodePanelContext(Player viewer, NodeKey key, NodeRecord record, int nodeWeavingLevel) {
}
