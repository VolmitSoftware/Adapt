package art.arcane.adapt.api.golem.node;

public final class NodeArea {
  private NodeArea() {
  }

  public static int[] center(NodeKey key) {
    return new int[]{key.x() + key.face().getModX(), key.y() + key.face().getModY(), key.z() + key.face().getModZ()};
  }

  public static int volume(NodeRecord record) {
    return span(record.radiusX()) * span(record.radiusY()) * span(record.radiusZ());
  }

  public static int[] position(NodeKey key, NodeRecord record, int index) {
    int spanX = span(record.radiusX());
    int spanZ = span(record.radiusZ());
    int offsetX = index % spanX;
    int offsetZ = (index / spanX) % spanZ;
    int offsetY = index / (spanX * spanZ);
    int[] center = center(key);
    return new int[]{center[0] - record.radiusX() + offsetX, center[1] - record.radiusY() + offsetY, center[2] - record.radiusZ() + offsetZ};
  }

  private static int span(int radius) {
    return radius * 2 + 1;
  }
}
