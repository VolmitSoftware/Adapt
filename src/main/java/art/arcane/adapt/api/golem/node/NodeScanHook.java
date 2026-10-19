package art.arcane.adapt.api.golem.node;

import java.util.UUID;

@FunctionalInterface
public interface NodeScanHook {
  void scan(NodeScanPass pass);

  default void inactive(NodeKey key) {
  }

  default void worldUnloaded(UUID world) {
  }
}
