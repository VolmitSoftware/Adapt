package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.UUID;

record GatherArea(UUID world, double x, double y, double z, double radius, List<Box> nodes, boolean byNodes) {
  GatherArea {
    nodes = List.copyOf(nodes);
  }

  static GatherArea home(Loc center, double radius) {
    return new GatherArea(center.world().uid(), center.x(), center.y(), center.z(), radius, List.of(), false);
  }

  static GatherArea nodes(UUID world, List<Box> boxes) {
    return new GatherArea(world, 0D, 0D, 0D, 0D, boxes, true);
  }

  boolean contains(Loc at) {
    if (!inWorld(at)) {
      return false;
    }
    if (byNodes) {
      for (Box box : nodes) {
        if (box.contains(at.x(), at.y(), at.z())) {
          return true;
        }
      }
      return false;
    }
    double dx = at.x() - x;
    double dy = at.y() - y;
    double dz = at.z() - z;
    return dx * dx + dy * dy + dz * dz <= radius * radius;
  }

  boolean collects(Loc at, Id type) {
    if (!byNodes) {
      return contains(at);
    }
    if (!inWorld(at)) {
      return false;
    }
    for (Box box : nodes) {
      if (box.contains(at.x(), at.y(), at.z()) && box.accepts(type)) {
        return true;
      }
    }
    return false;
  }

  private boolean inWorld(Loc at) {
    return at.world().uid().equals(world);
  }

  record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, NodeRecord record) {
    static Box of(NodeKey key, NodeRecord record) {
      int[] center = NodeArea.center(key);
      NodeCuboid area = record.area();
      return new Box(center[0] + area.minX(), center[1] + area.minY(), center[2] + area.minZ(),
          center[0] + area.maxX(), center[1] + area.maxY(), center[2] + area.maxZ(), record);
    }

    boolean contains(double x, double y, double z) {
      return within(x, minX, maxX) && within(y, minY, maxY) && within(z, minZ, maxZ);
    }

    boolean accepts(Id type) {
      return NodeFilters.accepts(record, type, NodeItemTags.shared());
    }

    double centerX() {
      return (minX + maxX + 1) / 2D;
    }

    double centerY() {
      return (minY + maxY + 1) / 2D;
    }

    double centerZ() {
      return (minZ + maxZ + 1) / 2D;
    }

    double halfX() {
      return (maxX - minX + 1) / 2D;
    }

    double halfY() {
      return (maxY - minY + 1) / 2D;
    }

    double halfZ() {
      return (maxZ - minZ + 1) / 2D;
    }

    private static boolean within(double coordinate, int min, int max) {
      int block = (int) Math.floor(coordinate);
      return block >= min && block <= max;
    }
  }
}
