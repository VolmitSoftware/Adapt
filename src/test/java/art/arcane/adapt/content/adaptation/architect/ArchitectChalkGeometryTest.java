package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectChalkGeometryTest {
  @Test
  void lineIncludesBothEndpointsWithoutVoxelGaps() {
    List<Point> points = ArchitectChalkGeometry.line(new Point(0, 0, 0), new Point(7, 4, -3));

    assertThat(points).startsWith(new Point(0, 0, 0)).endsWith(new Point(7, 4, -3));
    for (int index = 1; index < points.size(); index++) {
      Point previous = points.get(index - 1);
      Point current = points.get(index);
      assertThat(Math.abs(current.x() - previous.x())).isLessThanOrEqualTo(1);
      assertThat(Math.abs(current.y() - previous.y())).isLessThanOrEqualTo(1);
      assertThat(Math.abs(current.z() - previous.z())).isLessThanOrEqualTo(1);
    }
  }

  @Test
  void polylineKeepsOrderedCornersWithoutDuplicatingTheJoin() {
    Point corner = new Point(3, 0, 0);
    List<Point> points = ArchitectChalkGeometry.polyline(List.of(
        new Point(0, 0, 0),
        corner,
        new Point(3, 0, 3)
    ));

    assertThat(points).contains(corner);
    assertThat(points.stream().filter(corner::equals)).hasSize(1);
    assertThat(points).startsWith(new Point(0, 0, 0)).endsWith(new Point(3, 0, 3));
  }

  @Test
  void circleHonorsItsSelectedPlane() {
    Point center = new Point(10, 20, 30);
    List<Point> points = ArchitectChalkGeometry.circle(center, new Point(13, 25, 34), Plane.XZ);

    assertThat(points).isNotEmpty();
    assertThat(points).allMatch(point -> point.y() == center.y());
    assertThat(points).contains(
        new Point(15, 20, 30),
        new Point(5, 20, 30),
        new Point(10, 20, 35),
        new Point(10, 20, 25)
    );
  }

  @Test
  void arcPassesThroughAllThreeSelections() {
    Point start = new Point(-2, 0, 0);
    Point through = new Point(0, 2, 0);
    Point end = new Point(2, 0, 0);

    List<Point> points = ArchitectChalkGeometry.arc(start, through, end, 16D);

    assertThat(points).contains(start, through, end);
    assertThat(points).allMatch(point -> point.z() == 0);
  }

  @Test
  void arcRejectsCollinearSelections() {
    assertThat(ArchitectChalkGeometry.arc(
        new Point(0, 0, 0),
        new Point(1, 0, 0),
        new Point(2, 0, 0),
        16D
    )).isEmpty();
  }
}
