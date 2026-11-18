package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TamingPackLeaderAuraSpeedScalarTest {
  @Test
  void speedScalarMatchesVanillaSpeedPotionParityPerAmplifier() {
    assertThat(TamingPackLeaderAura.speedScalar(0)).isCloseTo(0.2, within(1.0e-9));
    assertThat(TamingPackLeaderAura.speedScalar(1)).isCloseTo(0.4, within(1.0e-9));
    assertThat(TamingPackLeaderAura.speedScalar(2)).isCloseTo(0.6, within(1.0e-9));
  }

  @Test
  void speedScalarFloorsNegativeAmplifiersToLevelOne() {
    assertThat(TamingPackLeaderAura.speedScalar(-3)).isEqualTo(TamingPackLeaderAura.speedScalar(0));
  }

  @Test
  void speedScalarIsMonotonicallyStrongerWithAmplifier() {
    assertThat(TamingPackLeaderAura.speedScalar(2)).isGreaterThan(TamingPackLeaderAura.speedScalar(1));
    assertThat(TamingPackLeaderAura.speedScalar(1)).isGreaterThan(TamingPackLeaderAura.speedScalar(0));
  }
}
