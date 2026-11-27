package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TamingPackLeaderAuraSpeedScalarTest {
  @Test
  void speedScalarMatchesVanillaSpeedPotionParityPerAmplifier() {
    assertThat(TamePackBuff.speedScalar(0)).isCloseTo(0.2, within(1.0e-9));
    assertThat(TamePackBuff.speedScalar(1)).isCloseTo(0.4, within(1.0e-9));
    assertThat(TamePackBuff.speedScalar(2)).isCloseTo(0.6, within(1.0e-9));
  }

  @Test
  void speedScalarFloorsNegativeAmplifiersToLevelOne() {
    assertThat(TamePackBuff.speedScalar(-3)).isEqualTo(TamePackBuff.speedScalar(0));
  }

  @Test
  void speedScalarIsMonotonicallyStrongerWithAmplifier() {
    assertThat(TamePackBuff.speedScalar(2)).isGreaterThan(TamePackBuff.speedScalar(1));
    assertThat(TamePackBuff.speedScalar(1)).isGreaterThan(TamePackBuff.speedScalar(0));
  }
}
