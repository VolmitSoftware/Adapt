package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeaborneOxygenAttributeTest {
  @Test
  void savedAirFractionScalesPerLevelAndClampsAtFullSustain() {
    assertThat(SeaborneBreath.savedAirFraction(1, 15, 75)).isCloseTo(0.2D, offset(0.0000001D));
    assertThat(SeaborneBreath.savedAirFraction(3, 15, 75)).isCloseTo(0.6D, offset(0.0000001D));
    assertThat(SeaborneBreath.savedAirFraction(5, 15, 75)).isEqualTo(1D);
    assertThat(SeaborneBreath.savedAirFraction(9, 15, 75)).isEqualTo(1D);
    assertThat(SeaborneBreath.savedAirFraction(0, 15, 75)).isZero();
    assertThat(SeaborneBreath.savedAirFraction(2, 0, 75)).isZero();
    assertThat(SeaborneBreath.savedAirFraction(2, -5, 75)).isZero();
  }

  @Test
  void oxygenBonusReproducesTheLegacyNetAirDrainRate() {
    assertThat(SeaborneBreath.oxygenBonus(0.2D)).isCloseTo(0.25D, offset(0.0000001D));
    assertThat(SeaborneBreath.oxygenBonus(0.4D)).isCloseTo(2D / 3D, offset(0.0000001D));
    assertThat(SeaborneBreath.oxygenBonus(0.8D)).isCloseTo(4D, offset(0.0000001D));
    assertThat(SeaborneBreath.oxygenBonus(1D)).isEqualTo(1024D);
    assertThat(SeaborneBreath.oxygenBonus(0D)).isZero();
    assertThat(SeaborneBreath.oxygenBonus(-0.5D)).isZero();
  }

  @Test
  void expectedSavedAirTicksMirrorsTheLegacyPerPulseGrant() {
    assertThat(SeaborneBreath.expectedSavedAirTicks(0.2D, 75)).isEqualTo(15);
    assertThat(SeaborneBreath.expectedSavedAirTicks(0.6D, 75)).isEqualTo(45);
    assertThat(SeaborneBreath.expectedSavedAirTicks(1D, 75)).isEqualTo(75);
    assertThat(SeaborneBreath.expectedSavedAirTicks(0D, 75)).isZero();
    assertThat(SeaborneBreath.expectedSavedAirTicks(0.5D, 0)).isZero();
  }
}
