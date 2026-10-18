package art.arcane.adapt.api.golem.node;

@FunctionalInterface
public interface NodeScanHook {
  void scan(NodeScanPass pass);
}
