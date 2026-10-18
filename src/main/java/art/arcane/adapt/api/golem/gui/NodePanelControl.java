package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

public record NodePanelControl(String id, ItemStack icon, String name, List<String> lore,
                               UnaryOperator<NodeRecord> leftClick, UnaryOperator<NodeRecord> rightClick) {
  public NodePanelControl {
    Objects.requireNonNull(id);
    Objects.requireNonNull(icon);
    Objects.requireNonNull(name);
    lore = List.copyOf(lore);
  }
}
