package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.NodeType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class NodePanels {
  public static final int MAX_CONTROLS = 9;
  private static final Map<NodeType, NodePanel> PANELS = new ConcurrentHashMap<>();

  private NodePanels() {
  }

  public static void register(NodeType type, NodePanel panel) {
    PANELS.put(Objects.requireNonNull(type), Objects.requireNonNull(panel));
  }

  public static void unregister(NodeType type, NodePanel panel) {
    PANELS.remove(Objects.requireNonNull(type), Objects.requireNonNull(panel));
  }

  public static Optional<NodePanel> of(NodeType type) {
    return Optional.ofNullable(PANELS.get(type));
  }
}
