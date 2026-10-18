package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

record GatherArea(UUID world, double x, double y, double z, double radius, List<Box> nodes) {
  GatherArea {
    nodes = List.copyOf(nodes);
  }

  static GatherArea home(Location center, double radius) {
    return new GatherArea(center.getWorld().getUID(), center.getX(), center.getY(), center.getZ(), radius, List.of());
  }

  static GatherArea nodes(UUID world, List<Box> boxes) {
    return new GatherArea(world, 0D, 0D, 0D, 0D, boxes);
  }

  boolean byNodes() {
    return !nodes.isEmpty();
  }

  boolean contains(Location at) {
    World atWorld = at.getWorld();
    if (atWorld == null || !atWorld.getUID().equals(world)) {
      return false;
    }
    if (byNodes()) {
      for (Box box : nodes) {
        if (box.contains(at.getX(), at.getY(), at.getZ())) {
          return true;
        }
      }
      return false;
    }
    double dx = at.getX() - x;
    double dy = at.getY() - y;
    double dz = at.getZ() - z;
    return dx * dx + dy * dy + dz * dz <= radius * radius;
  }

  record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static Box of(NodeKey key, NodeRecord record) {
      int[] center = NodeArea.center(key);
      return new Box(center[0] - record.radiusX(), center[1] - record.radiusY(), center[2] - record.radiusZ(),
          center[0] + record.radiusX(), center[1] + record.radiusY(), center[2] + record.radiusZ());
    }

    boolean contains(double x, double y, double z) {
      return within(x, minX, maxX) && within(y, minY, maxY) && within(z, minZ, maxZ);
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
