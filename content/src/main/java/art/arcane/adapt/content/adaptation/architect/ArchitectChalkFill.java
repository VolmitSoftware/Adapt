package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.content.item.ChalkWandItem.Plan;
import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The blocks a chalk fill lays along a finished guide, and the ordered path a template repeat walks. */
public final class ArchitectChalkFill {
  private ArchitectChalkFill() {
  }

  public enum Shape { OUTLINE, DISC, WALL }

  /**
   * The finished guide as one ordered path: lines and polylines from start to end, arcs from start through to end,
   * circles once round by angle without their center. Empty while the guide is unfinished.
   */
  public static List<Point> path(Tool tool, Plan plan, double maxArcRadius) {
    if (plan == null || plan.isEmpty()) {
      return List.of();
    }
    List<Point> points = plan.points();
    return switch (tool) {
      case STRAIGHTEDGE -> points.size() == 2 ? ArchitectChalkGeometry.line(points.getFirst(), points.get(1)) : List.of();
      case POLYLINE -> points.size() >= 2 ? ArchitectChalkGeometry.polyline(points) : List.of();
      case COMPASS -> points.size() == 2 ? aroundCenter(points.getFirst(), plan.plane(),
          ArchitectChalkGeometry.circle(points.getFirst(), points.get(1), plan.plane())) : List.of();
      case ARC_BOW -> points.size() == 3
          ? ArchitectChalkGeometry.arc(points.getFirst(), points.get(1), points.get(2), maxArcRadius) : List.of();
    };
  }

  /**
   * The blocks to lay, in build order. An outline follows the path; a disc fills a circle out from its center; a wall
   * stacks the outline {@code height} layers along the guide plane's normal, upwards for flat guides and towards
   * {@code towards} (+1 or -1) for upright circles.
   */
  public static List<Point> fill(Tool tool, Plan plan, Shape shape, int height, int towards, double maxArcRadius) {
    List<Point> base = shape == Shape.DISC && tool == Tool.COMPASS && plan != null && plan.points().size() == 2
        ? disc(plan.points().getFirst(), plan.points().get(1), plan.plane())
        : path(tool, plan, maxArcRadius);
    if (shape != Shape.WALL || height <= 1 || base.isEmpty()) {
      return base;
    }
    Plane plane = tool == Tool.COMPASS ? plan.plane() : Plane.XZ;
    int step = plane == Plane.XZ ? 1 : (towards < 0 ? -1 : 1);
    Set<Point> layers = new LinkedHashSet<>(base.size() * height);
    for (int layer = 0; layer < height; layer++) {
      int offset = layer * step;
      for (Point point : base) {
        layers.add(switch (plane) {
          case XZ -> new Point(point.x(), point.y() + offset, point.z());
          case XY -> new Point(point.x(), point.y(), point.z() + offset);
          case YZ -> new Point(point.x() + offset, point.y(), point.z());
        });
      }
    }
    return List.copyOf(layers);
  }

  /** Every block of the circle's plane within its radius, nearest the center first. */
  static List<Point> disc(Point center, Point radiusPoint, Plane plane) {
    List<Point> ring = ArchitectChalkGeometry.circle(center, radiusPoint, plane);
    if (ring.isEmpty()) {
      return List.of();
    }
    double radius = 0D;
    for (Point point : ring) {
      radius = Math.max(radius, Math.sqrt(ArchitectChalkGeometry.squaredDistance(center, point)));
    }
    int reach = (int) Math.ceil(radius);
    double limit = (radius + 0.5D) * (radius + 0.5D);
    List<Point> disc = new ArrayList<>();
    for (int u = -reach; u <= reach; u++) {
      for (int v = -reach; v <= reach; v++) {
        if ((u * u) + (v * v) <= limit) {
          disc.add(inPlane(center, plane, u, v));
        }
      }
    }
    disc.sort(Comparator.comparingDouble((Point point) -> ArchitectChalkGeometry.squaredDistance(center, point)));
    return List.copyOf(disc);
  }

  private static List<Point> aroundCenter(Point center, Plane plane, List<Point> ring) {
    List<Point> ordered = new ArrayList<>(ring);
    ordered.sort(Comparator.comparingDouble((Point point) -> angle(center, plane, point)));
    return List.copyOf(ordered);
  }

  private static double angle(Point center, Plane plane, Point point) {
    return switch (plane) {
      case XZ -> Math.atan2(point.z() - center.z(), point.x() - center.x());
      case XY -> Math.atan2(point.y() - center.y(), point.x() - center.x());
      case YZ -> Math.atan2(point.z() - center.z(), point.y() - center.y());
    };
  }

  private static Point inPlane(Point center, Plane plane, int u, int v) {
    return switch (plane) {
      case XZ -> new Point(center.x() + u, center.y(), center.z() + v);
      case XY -> new Point(center.x() + u, center.y() + v, center.z());
      case YZ -> new Point(center.x(), center.y() + u, center.z() + v);
    };
  }
}
