package art.arcane.adapt.content.golem.node;

import java.util.UUID;

@FunctionalInterface
public interface NodeScanHook {
  void scan(NodeScanPass pass);

  default void inactive(NodeKey key) {
  }

  default void invalidated(NodeKey key) {
  }

  default void worldUnloaded(UUID world) {
  }
}
