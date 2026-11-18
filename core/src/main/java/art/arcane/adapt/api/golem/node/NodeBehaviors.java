package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeBehaviors {
  private static final Map<NodeType, NodeBehavior> BEHAVIORS = new ConcurrentHashMap<>();

  private NodeBehaviors() {
  }

  public static void register(NodeType type, NodeBehavior behavior) {
    BEHAVIORS.put(Objects.requireNonNull(type), Objects.requireNonNull(behavior));
  }

  public static void unregister(NodeType type, NodeBehavior behavior) {
    BEHAVIORS.remove(type, behavior);
  }

  public static Optional<NodeBehavior> of(NodeType type) {
    return Optional.ofNullable(BEHAVIORS.get(type));
  }
}
