package art.arcane.adapt.api.golem.node;

public final class NodeArea {
  private NodeArea() {
  }

  public static int[] center(NodeKey key) {
    return new int[]{key.x() + key.face().getModX(), key.y() + key.face().getModY(), key.z() + key.face().getModZ()};
  }

  public static boolean covers(NodeKey key, NodeRecord record, double x, double y, double z) {
    int[] center = center(key);
    return record.area().contains(block(x) - center[0], block(y) - center[1], block(z) - center[2]);
  }

  public static int volume(NodeRecord record) {
    return record.area().volume();
  }

  public static boolean inWorld(NodeKey key, NodeCuboid area, int minimumHeight, int maximumHeight) {
    int centerY = key.y() + key.face().getModY();
    return (long) centerY + area.minY() >= minimumHeight && (long) centerY + area.maxY() < maximumHeight;
  }

  public static int[] position(NodeKey key, NodeRecord record, int index) {
    NodeCuboid area = record.area();
    int spanX = area.spanX();
    int spanZ = area.spanZ();
    int offsetX = index % spanX;
    int offsetZ = (index / spanX) % spanZ;
    int offsetY = index / (spanX * spanZ);
    int[] center = center(key);
    return new int[]{center[0] + area.minX() + offsetX, center[1] + area.minY() + offsetY, center[2] + area.minZ() + offsetZ};
  }

  private static int block(double coordinate) {
    return (int) Math.floor(coordinate);
  }
}
