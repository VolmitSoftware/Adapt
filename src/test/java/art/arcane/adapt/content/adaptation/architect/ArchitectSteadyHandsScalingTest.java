package art.arcane.adapt.content.adaptation.architect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectSteadyHandsScalingTest {
  @Test
  void hasteBreakSpeedBonusMatchesHastePotionScaling() {
    assertThat(ArchitectSteadyHands.hasteBreakSpeedBonus(0)).isEqualTo(0.2D);
    assertThat(ArchitectSteadyHands.hasteBreakSpeedBonus(1)).isEqualTo(0.4D);
    assertThat(ArchitectSteadyHands.hasteBreakSpeedBonus(4)).isEqualTo(1.0D);
  }

  @Test
  void graceDurationTicksConvertsMillisAndClampsToOneTick() {
    assertThat(ArchitectSteadyHands.graceDurationTicks(4000L)).isEqualTo(80L);
    assertThat(ArchitectSteadyHands.graceDurationTicks(1975L)).isEqualTo(39L);
    assertThat(ArchitectSteadyHands.graceDurationTicks(50L)).isEqualTo(1L);
    assertThat(ArchitectSteadyHands.graceDurationTicks(0L)).isEqualTo(1L);
    assertThat(ArchitectSteadyHands.graceDurationTicks(-500L)).isEqualTo(1L);
  }

  @Test
  void shieldedHeightLerpsBetweenConfiguredBounds() {
    assertThat(ArchitectSteadyHands.shieldedHeight(3D, 12D, 0D)).isEqualTo(3D);
    assertThat(ArchitectSteadyHands.shieldedHeight(3D, 12D, 1D)).isEqualTo(12D);
    assertThat(ArchitectSteadyHands.shieldedHeight(3D, 12D, 0.5D)).isEqualTo(7.5D);
  }
}
