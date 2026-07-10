package art.arcane.adapt.util.common.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NearestBlockPositionsTest {
  @Test
  void retainsOnlyNearestPositionsInAscendingOrder() {
    NearestBlockPositions positions = new NearestBlockPositions(3);
    positions.offer(9, 0, 0, 9D);
    positions.offer(1, 0, 0, 1D);
    positions.offer(4, 0, 0, 4D);

    assertThat(positions.offer(2, 0, 0, 2D)).isTrue();
    assertThat(positions.size()).isEqualTo(3);
    assertThat(positions.distanceSquared(0)).isEqualTo(1D);
    assertThat(positions.distanceSquared(1)).isEqualTo(2D);
    assertThat(positions.distanceSquared(2)).isEqualTo(4D);
    assertThat(positions.x(0)).isEqualTo(1);
    assertThat(positions.x(1)).isEqualTo(2);
    assertThat(positions.x(2)).isEqualTo(4);
  }

  @Test
  void ignoresDuplicateAndFartherPositionsAtCapacity() {
    NearestBlockPositions positions = new NearestBlockPositions(2);
    positions.offer(1, 2, 3, 1D);
    positions.offer(4, 5, 6, 2D);

    assertThat(positions.offer(1, 2, 3, 0.5D)).isFalse();
    assertThat(positions.offer(7, 8, 9, 3D)).isFalse();
    assertThat(positions.size()).isEqualTo(2);
    assertThat(positions.distanceSquared(0)).isEqualTo(1D);
    assertThat(positions.distanceSquared(1)).isEqualTo(2D);
  }

  @Test
  void zeroCapacityRejectsCandidatesWithoutAllocationGrowth() {
    NearestBlockPositions positions = new NearestBlockPositions(0);

    assertThat(positions.offer(1, 2, 3, 1D)).isFalse();
    assertThat(positions.size()).isZero();
    assertThatThrownBy(() -> positions.x(0)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void rejectsInvalidCapacityAndDistances() {
    assertThatThrownBy(() -> new NearestBlockPositions(-1))
        .isInstanceOf(IllegalArgumentException.class);

    NearestBlockPositions positions = new NearestBlockPositions(1);
    assertThatThrownBy(() -> positions.offer(0, 0, 0, -1D))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> positions.offer(0, 0, 0, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
