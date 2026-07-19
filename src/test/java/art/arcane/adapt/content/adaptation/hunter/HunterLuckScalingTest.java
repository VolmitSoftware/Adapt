package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HunterLuckScalingTest {
  @Test
  void luckBonusMatchesLuckEffectAmplifierParity() {
    assertThat(HunterLuck.luckBonus(1)).isCloseTo(2.0D, within(1.0e-9D));
    assertThat(HunterLuck.luckBonus(3)).isCloseTo(4.0D, within(1.0e-9D));
    assertThat(HunterLuck.luckBonus(5)).isCloseTo(6.0D, within(1.0e-9D));
  }

  @Test
  void unluckAmountMirrorsUnluckEffectAmplifierParity() {
    assertThat(HunterLuck.unluckAmount(6, 1)).isCloseTo(-6.0D, within(1.0e-9D));
    assertThat(HunterLuck.unluckAmount(6, 5)).isCloseTo(-2.0D, within(1.0e-9D));
    assertThat(HunterLuck.unluckAmount(6, 6)).isCloseTo(-1.0D, within(1.0e-9D));
  }

  @Test
  void unluckAmountIsNeutralWhenLevelExceedsBaseByOne() {
    assertThat(HunterLuck.unluckAmount(6, 7)).isCloseTo(0.0D, within(1.0e-9D));
  }

  @Test
  void buffDurationTicksScalesWithLevel() {
    assertThat(HunterLuck.buffDurationTicks(100, 1, 0L, false)).isEqualTo(100L);
    assertThat(HunterLuck.buffDurationTicks(100, 3, 0L, false)).isEqualTo(300L);
  }

  @Test
  void buffDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertThat(HunterLuck.buffDurationTicks(100, 2, 60L, true)).isEqualTo(260L);
    assertThat(HunterLuck.buffDurationTicks(100, 2, 60L, false)).isEqualTo(200L);
  }

  @Test
  void buffDurationTicksClampsNegativeRemaining() {
    assertThat(HunterLuck.buffDurationTicks(100, 1, -40L, true)).isEqualTo(100L);
  }

  @Test
  void buffDurationTicksNonPositiveBaseYieldsZero() {
    assertThat(HunterLuck.buffDurationTicks(0, 3, 500L, true)).isEqualTo(0L);
    assertThat(HunterLuck.buffDurationTicks(-50, 2, 500L, true)).isEqualTo(0L);
    assertThat(HunterLuck.buffDurationTicks(100, 0, 500L, true)).isEqualTo(0L);
  }

  @Test
  void penaltyDurationTicksUsesFlatBase() {
    assertThat(HunterLuck.penaltyDurationTicks(50, 0L, false)).isEqualTo(50L);
    assertThat(HunterLuck.penaltyDurationTicks(50, 30L, false)).isEqualTo(50L);
  }

  @Test
  void penaltyDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertThat(HunterLuck.penaltyDurationTicks(50, 30L, true)).isEqualTo(80L);
    assertThat(HunterLuck.penaltyDurationTicks(50, -10L, true)).isEqualTo(50L);
  }

  @Test
  void penaltyDurationTicksNonPositiveBaseYieldsZero() {
    assertThat(HunterLuck.penaltyDurationTicks(0, 500L, true)).isEqualTo(0L);
    assertThat(HunterLuck.penaltyDurationTicks(-5, 500L, true)).isEqualTo(0L);
  }
}
