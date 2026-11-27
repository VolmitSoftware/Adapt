/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ArchitectChalkGeometry {
  private static final int MAX_LINE_STEPS = 4_096;
  private static final int MAX_CIRCLE_RADIUS = 512;
  private static final int MAX_ARC_SAMPLES = 2_048;
  private static final double TWO_PI = Math.PI * 2D;
  private static final double EPSILON = 1.0E-8D;

  private ArchitectChalkGeometry() {
  }

  static List<Point> line(Point start, Point end) {
    int deltaX = end.x() - start.x();
    int deltaY = end.y() - start.y();
    int deltaZ = end.z() - start.z();
    int steps = Math.max(Math.abs(deltaX), Math.max(Math.abs(deltaY), Math.abs(deltaZ)));
    if (steps > MAX_LINE_STEPS) {
      return List.of();
    }
    if (steps == 0) {
      return List.of(start);
    }

    Set<Point> points = new LinkedHashSet<>(steps + 1);
    for (int index = 0; index <= steps; index++) {
      double factor = index / (double) steps;
      points.add(new Point(
          (int) Math.round(start.x() + (deltaX * factor)),
          (int) Math.round(start.y() + (deltaY * factor)),
          (int) Math.round(start.z() + (deltaZ * factor))
      ));
    }
    return List.copyOf(points);
  }

  static List<Point> polyline(List<Point> vertices) {
    if (vertices == null || vertices.isEmpty()) {
      return List.of();
    }
    if (vertices.size() == 1) {
      return List.of(vertices.getFirst());
    }

    Set<Point> points = new LinkedHashSet<>();
    for (int index = 1; index < vertices.size(); index++) {
      List<Point> segment = line(vertices.get(index - 1), vertices.get(index));
      if (segment.isEmpty()) {
        return List.of();
      }
      points.addAll(segment);
    }
    return List.copyOf(points);
  }

  static List<Point> circle(Point center, Point radiusPoint, Plane plane) {
    Point projected = project(center, radiusPoint, plane);
    int radius = (int) Math.round(Math.sqrt(squaredDistance(center, projected)));
    if (radius <= 0 || radius > MAX_CIRCLE_RADIUS) {
      return List.of();
    }

    Set<Point> points = new LinkedHashSet<>();
    int primary = radius;
    int secondary = 0;
    int decision = 1 - radius;
    while (primary >= secondary) {
      addCircleSymmetry(points, center, plane, primary, secondary);
      secondary++;
      if (decision < 0) {
        decision += (2 * secondary) + 1;
      } else {
        primary--;
        decision += (2 * (secondary - primary)) + 1;
      }
    }
    return List.copyOf(points);
  }

  static List<Point> arc(Point start, Point through, Point end, double maxRadius) {
    if (start.equals(through) || start.equals(end) || through.equals(end)) {
      return List.of();
    }

    Vec startVector = Vec.of(start);
    Vec throughVector = Vec.of(through);
    Vec endVector = Vec.of(end);
    Vec first = throughVector.subtract(startVector);
    Vec second = endVector.subtract(startVector);
    Vec normal = first.cross(second);
    double normalLengthSquared = normal.lengthSquared();
    if (normalLengthSquared <= EPSILON) {
      return List.of();
    }

    double denominator = 2D * normalLengthSquared;
    Vec center = startVector.add(
        second.cross(normal).multiply(first.lengthSquared())
            .add(normal.cross(first).multiply(second.lengthSquared()))
            .multiply(1D / denominator)
    );
    double radius = startVector.subtract(center).length();
    if (!Double.isFinite(radius) || radius <= EPSILON || radius > Math.max(1D, maxRadius)) {
      return List.of();
    }

    Vec basisU = startVector.subtract(center).multiply(1D / radius);
    Vec basisV = normal.multiply(1D / Math.sqrt(normalLengthSquared)).cross(basisU);
    double throughAngle = positiveAngle(angle(throughVector.subtract(center), basisU, basisV));
    double endAngle = positiveAngle(angle(endVector.subtract(center), basisU, basisV));
    double throughSweep;
    double endSweep;
    if (throughAngle <= endAngle) {
      throughSweep = throughAngle;
      endSweep = endAngle;
    } else {
      throughSweep = throughAngle - TWO_PI;
      endSweep = endAngle - TWO_PI;
    }

    Set<Point> points = new LinkedHashSet<>();
    appendArc(points, center, basisU, basisV, radius, 0D, throughSweep);
    points.add(through);
    appendArc(points, center, basisU, basisV, radius, throughSweep, endSweep);
    points.add(end);
    return List.copyOf(points);
  }

  static double squaredDistance(Point first, Point second) {
    long deltaX = (long) first.x() - second.x();
    long deltaY = (long) first.y() - second.y();
    long deltaZ = (long) first.z() - second.z();
    return (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ);
  }

  private static Point project(Point center, Point point, Plane plane) {
    return switch (plane) {
      case XY -> new Point(point.x(), point.y(), center.z());
      case XZ -> new Point(point.x(), center.y(), point.z());
      case YZ -> new Point(center.x(), point.y(), point.z());
    };
  }

  private static void addCircleSymmetry(Set<Point> points, Point center, Plane plane,
                                        int primary, int secondary) {
    addCirclePoint(points, center, plane, primary, secondary);
    addCirclePoint(points, center, plane, secondary, primary);
    addCirclePoint(points, center, plane, -secondary, primary);
    addCirclePoint(points, center, plane, -primary, secondary);
    addCirclePoint(points, center, plane, -primary, -secondary);
    addCirclePoint(points, center, plane, -secondary, -primary);
    addCirclePoint(points, center, plane, secondary, -primary);
    addCirclePoint(points, center, plane, primary, -secondary);
  }

  private static void addCirclePoint(Set<Point> points, Point center, Plane plane,
                                     int first, int second) {
    Point point = switch (plane) {
      case XY -> new Point(center.x() + first, center.y() + second, center.z());
      case XZ -> new Point(center.x() + first, center.y(), center.z() + second);
      case YZ -> new Point(center.x(), center.y() + first, center.z() + second);
    };
    points.add(point);
  }

  private static void appendArc(Set<Point> points, Vec center, Vec basisU, Vec basisV,
                                double radius, double startAngle, double endAngle) {
    double arcLength = Math.abs(endAngle - startAngle) * radius;
    int steps = Math.max(1, Math.min(MAX_ARC_SAMPLES, (int) Math.ceil(arcLength * 2D)));
    Point previous = null;
    for (int index = 0; index <= steps; index++) {
      double factor = index / (double) steps;
      double currentAngle = startAngle + ((endAngle - startAngle) * factor);
      Vec location = center
          .add(basisU.multiply(Math.cos(currentAngle) * radius))
          .add(basisV.multiply(Math.sin(currentAngle) * radius));
      Point current = location.round();
      if (previous == null) {
        points.add(current);
      } else {
        points.addAll(line(previous, current));
      }
      previous = current;
    }
  }

  private static double angle(Vec vector, Vec basisU, Vec basisV) {
    return Math.atan2(vector.dot(basisV), vector.dot(basisU));
  }

  private static double positiveAngle(double angle) {
    double positive = angle % TWO_PI;
    return positive < 0D ? positive + TWO_PI : positive;
  }

  private record Vec(double x, double y, double z) {
    private static Vec of(Point point) {
      return new Vec(point.x(), point.y(), point.z());
    }

    private Vec add(Vec other) {
      return new Vec(x + other.x, y + other.y, z + other.z);
    }

    private Vec subtract(Vec other) {
      return new Vec(x - other.x, y - other.y, z - other.z);
    }

    private Vec multiply(double factor) {
      return new Vec(x * factor, y * factor, z * factor);
    }

    private double dot(Vec other) {
      return (x * other.x) + (y * other.y) + (z * other.z);
    }

    private Vec cross(Vec other) {
      return new Vec(
          (y * other.z) - (z * other.y),
          (z * other.x) - (x * other.z),
          (x * other.y) - (y * other.x)
      );
    }

    private double lengthSquared() {
      return dot(this);
    }

    private double length() {
      return Math.sqrt(lengthSquared());
    }

    private Point round() {
      return new Point((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
    }
  }
}
