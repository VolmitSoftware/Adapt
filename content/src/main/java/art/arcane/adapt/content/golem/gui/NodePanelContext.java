package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.PlayerRef;

public record NodePanelContext(PlayerRef viewer, NodeKey key, NodeRecord record, int nodeWeavingLevel) {
}
