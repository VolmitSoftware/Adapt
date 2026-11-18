package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.PlayerRef;

public record NodePanelContext(PlayerRef viewer, NodeKey key, NodeRecord record, int nodeWeavingLevel) {
}
