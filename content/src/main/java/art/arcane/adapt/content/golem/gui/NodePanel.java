package art.arcane.adapt.content.golem.gui;

import java.util.List;

@FunctionalInterface
public interface NodePanel {
  List<NodePanelControl> controls(NodePanelContext context);
}
