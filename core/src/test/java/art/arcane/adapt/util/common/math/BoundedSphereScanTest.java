package art.arcane.adapt.util.common.math;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedSphereScanTest {
  @Test
  void stopsAtBudgetWithoutDuplicatesOrOutOfRangeOffsets() {
    int radius = 29;
    int budget = 1024;
    BoundedSphereScan.Cursor cursor = BoundedSphereScan.cursor(radius, 5, budget, 42);
    Set<String> positions = new HashSet<>(budget);

    while (cursor.advance()) {
      long distanceSquared = ((long) cursor.x() * cursor.x())
          + ((long) cursor.y() * cursor.y())
          + ((long) cursor.z() * cursor.z());
      assertThat(distanceSquared).isLessThanOrEqualTo((long) radius * radius);
      positions.add(cursor.x() + ":" + cursor.y() + ":" + cursor.z());
    }

    assertThat(cursor.samples()).isEqualTo(budget);
    assertThat(positions).hasSize(budget);
  }

  @Test
  void visitsDenseSphereBeforeDistributedSamples() {
    int denseRadius = 3;
    int densePoints = countSpherePoints(denseRadius);
    BoundedSphereScan.Cursor cursor = BoundedSphereScan.cursor(12, denseRadius, densePoints + 1, 7);

    for (int i = 0; i < densePoints; i++) {
      assertThat(cursor.advance()).isTrue();
      assertThat(cursor.distanceSquared()).isLessThanOrEqualTo((long) denseRadius * denseRadius);
    }

    assertThat(cursor.advance()).isTrue();
    assertThat(cursor.distanceSquared()).isGreaterThan((long) denseRadius * denseRadius);
    assertThat(cursor.advance()).isFalse();
  }

  @Test
  void coversWholeSphereWhenBudgetExceedsAvailablePositions() {
    int radius = 4;
    BoundedSphereScan.Cursor cursor = BoundedSphereScan.cursor(radius, 2, Integer.MAX_VALUE, 19);
    int count = 0;
    while (cursor.advance()) {
      count++;
    }

    assertThat(count).isEqualTo(countSpherePoints(radius));
  }

  @Test
  void producesStableDistributedOrderForSameSeed() {
    assertThat(firstPositions(13, 4, 400, 1234))
        .isEqualTo(firstPositions(13, 4, 400, 1234));
    assertThat(firstPositions(13, 4, 400, 1234))
        .isNotEqualTo(firstPositions(13, 4, 400, 5678));
  }

  @Test
  void resumesAcrossSlicesWithoutChangingCursorOrder() {
    List<String> expected = firstPositions(18, 5, 1500, 91);
    BoundedSphereScan.Cursor cursor = BoundedSphereScan.cursor(18, 5, 1500, 91);
    ArrayList<String> sliced = new ArrayList<>(1500);

    while (sliced.size() < 1500) {
      int sliceEnd = Math.min(1500, sliced.size() + 37);
      while (sliced.size() < sliceEnd && cursor.advance()) {
        sliced.add(cursor.x() + ":" + cursor.y() + ":" + cursor.z());
      }
    }

    assertThat(sliced).isEqualTo(expected);
    assertThat(cursor.advance()).isFalse();
  }

  @Test
  void rejectsInvalidLimits() {
    assertThatThrownBy(() -> BoundedSphereScan.cursor(-1, 0, 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BoundedSphereScan.cursor(1, 0, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private List<String> firstPositions(int radius, int denseRadius, int budget, int seed) {
    BoundedSphereScan.Cursor cursor = BoundedSphereScan.cursor(radius, denseRadius, budget, seed);
    ArrayList<String> result = new ArrayList<>(budget);
    while (cursor.advance()) {
      result.add(cursor.x() + ":" + cursor.y() + ":" + cursor.z());
    }
    return result;
  }

  private int countSpherePoints(int radius) {
    int count = 0;
    int radiusSquared = radius * radius;
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          if ((x * x) + (y * y) + (z * z) <= radiusSquared) {
            count++;
          }
        }
      }
    }
    return count;
  }
}
