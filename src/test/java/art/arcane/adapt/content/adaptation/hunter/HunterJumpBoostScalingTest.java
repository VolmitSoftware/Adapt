package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HunterJumpBoostScalingTest {
  @Test
  void jumpStrengthBonusMatchesJumpBoostAmplifierParity() {
    assertThat(HunterJumpBoost.jumpStrengthBonus(1)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(HunterJumpBoost.jumpStrengthBonus(2)).isCloseTo(0.3D, within(1.0e-9D));
    assertThat(HunterJumpBoost.jumpStrengthBonus(5)).isCloseTo(0.6D, within(1.0e-9D));
  }

  @Test
  void safeFallBonusAddsOneBlockPerAmplifierTier() {
    assertThat(HunterJumpBoost.safeFallBonus(1)).isCloseTo(2.0D, within(1.0e-9D));
    assertThat(HunterJumpBoost.safeFallBonus(2)).isCloseTo(3.0D, within(1.0e-9D));
    assertThat(HunterJumpBoost.safeFallBonus(5)).isCloseTo(6.0D, within(1.0e-9D));
  }

  @Test
  void buffDurationTicksScalesWithLevel() {
    assertThat(HunterJumpBoost.buffDurationTicks(100, 1, 0L, false)).isEqualTo(100L);
    assertThat(HunterJumpBoost.buffDurationTicks(100, 3, 0L, false)).isEqualTo(300L);
    assertThat(HunterJumpBoost.buffDurationTicks(40, 2, 0L, true)).isEqualTo(80L);
  }

  @Test
  void buffDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertThat(HunterJumpBoost.buffDurationTicks(100, 2, 60L, true)).isEqualTo(260L);
    assertThat(HunterJumpBoost.buffDurationTicks(100, 2, 60L, false)).isEqualTo(200L);
  }

  @Test
  void buffDurationTicksClampsNegativeRemaining() {
    assertThat(HunterJumpBoost.buffDurationTicks(100, 1, -40L, true)).isEqualTo(100L);
  }

  @Test
  void buffDurationTicksNonPositiveBaseYieldsZero() {
    assertThat(HunterJumpBoost.buffDurationTicks(0, 3, 500L, true)).isEqualTo(0L);
    assertThat(HunterJumpBoost.buffDurationTicks(-50, 2, 500L, true)).isEqualTo(0L);
    assertThat(HunterJumpBoost.buffDurationTicks(100, 0, 500L, true)).isEqualTo(0L);
  }
}
