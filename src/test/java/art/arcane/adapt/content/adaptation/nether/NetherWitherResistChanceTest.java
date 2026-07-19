package art.arcane.adapt.content.adaptation.nether;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetherWitherResistChanceTest {
  @Test
  void zeroChanceNeverSucceeds() {
    assertThat(NetherWitherResist.winsChanceRoll(0D, 0D)).isFalse();
    assertThat(NetherWitherResist.winsChanceRoll(0D, Math.nextDown(100D))).isFalse();
  }

  @Test
  void fractionalChanceUsesExactPercentageThreshold() {
    assertThat(NetherWitherResist.winsChanceRoll(10.5D, Math.nextDown(10.5D))).isTrue();
    assertThat(NetherWitherResist.winsChanceRoll(10.5D, 10.5D)).isFalse();
  }

  @Test
  void interiorChanceUsesExclusiveUpperThreshold() {
    assertThat(NetherWitherResist.winsChanceRoll(50D, Math.nextDown(50D))).isTrue();
    assertThat(NetherWitherResist.winsChanceRoll(50D, 50D)).isFalse();
  }

  @Test
  void fullChanceSucceedsForEveryValidRollBoundary() {
    assertThat(NetherWitherResist.winsChanceRoll(100D, 0D)).isTrue();
    assertThat(NetherWitherResist.winsChanceRoll(100D, Math.nextDown(100D))).isTrue();
  }

  @Test
  void invalidAndOutOfRangeValuesAreHandledSafely() {
    assertThat(NetherWitherResist.winsChanceRoll(-1D, 0D)).isFalse();
    assertThat(NetherWitherResist.winsChanceRoll(Double.NaN, 0D)).isFalse();
    assertThat(NetherWitherResist.winsChanceRoll(Double.POSITIVE_INFINITY, 0D)).isFalse();
    assertThat(NetherWitherResist.winsChanceRoll(Double.NEGATIVE_INFINITY, 0D)).isFalse();
    assertThat(NetherWitherResist.winsChanceRoll(150D, Math.nextDown(100D))).isTrue();
    assertThat(NetherWitherResist.winsChanceRoll(100D, -Double.MIN_VALUE)).isFalse();
    assertThat(NetherWitherResist.winsChanceRoll(100D, 100D)).isFalse();
  }
}
