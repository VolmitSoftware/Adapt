package art.arcane.adapt.content.adaptation.ranged;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangedForceOwnerThreadTest {
  @Test
  void longRangeThresholdRemainsStrictlyBeyondThirtyBlocks() {
    assertThat(RangedForce.isLongRangeHit(900D)).isFalse();
    assertThat(RangedForce.isLongRangeHit(900.0001D)).isTrue();
  }
}
