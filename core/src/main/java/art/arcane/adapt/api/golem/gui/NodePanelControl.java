package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public record NodePanelControl(String id, ItemRef icon, String name, List<String> lore,
                               UnaryOperator<NodeRecord> leftClick, UnaryOperator<NodeRecord> rightClick, Consumer<PlayerRef> open) {
  public NodePanelControl {
    Objects.requireNonNull(id);
    Objects.requireNonNull(icon);
    Objects.requireNonNull(name);
    lore = List.copyOf(lore);
    if (leftClick != null && open != null) {
      throw new IllegalArgumentException("Node panel control " + id + " cannot both edit the node and open a menu on left-click");
    }
  }
}
