package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HunterStrengthScalingTest {
  @Test
  void strengthDamageBonusMatchesStrengthAmplifierParity() {
    assertThat(HunterStrength.strengthDamageBonus(1)).isCloseTo(6.0D, within(1.0e-9D));
    assertThat(HunterStrength.strengthDamageBonus(2)).isCloseTo(9.0D, within(1.0e-9D));
    assertThat(HunterStrength.strengthDamageBonus(5)).isCloseTo(18.0D, within(1.0e-9D));
  }

  @Test
  void buffDurationTicksScalesWithLevel() {
    assertThat(HunterStrength.buffDurationTicks(25, 1, 0L, false)).isEqualTo(25L);
    assertThat(HunterStrength.buffDurationTicks(25, 4, 0L, false)).isEqualTo(100L);
    assertThat(HunterStrength.buffDurationTicks(40, 2, 0L, true)).isEqualTo(80L);
  }

  @Test
  void buffDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertThat(HunterStrength.buffDurationTicks(25, 2, 30L, true)).isEqualTo(80L);
    assertThat(HunterStrength.buffDurationTicks(25, 2, 30L, false)).isEqualTo(50L);
  }

  @Test
  void buffDurationTicksClampsNegativeRemaining() {
    assertThat(HunterStrength.buffDurationTicks(25, 1, -40L, true)).isEqualTo(25L);
  }

  @Test
  void buffDurationTicksNonPositiveBaseYieldsZero() {
    assertThat(HunterStrength.buffDurationTicks(0, 3, 500L, true)).isEqualTo(0L);
    assertThat(HunterStrength.buffDurationTicks(-25, 2, 500L, true)).isEqualTo(0L);
    assertThat(HunterStrength.buffDurationTicks(25, 0, 500L, true)).isEqualTo(0L);
  }
}
