package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeaborneTurtleMinerFatigueCounterTest {
  @Test
  void fatigueFactorMatchesVanillaDestroySpeedTable() {
    assertThat(SeaborneTurtlesMiningSpeed.fatigueFactor(0D)).isCloseTo(0.3D, offset(1.0E-12D));
    assertThat(SeaborneTurtlesMiningSpeed.fatigueFactor(1D)).isCloseTo(0.09D, offset(1.0E-12D));
    assertThat(SeaborneTurtlesMiningSpeed.fatigueFactor(2D)).isCloseTo(0.0027D, offset(1.0E-12D));
    assertThat(SeaborneTurtlesMiningSpeed.fatigueFactor(3D)).isCloseTo(8.1E-4D, offset(1.0E-12D));
    assertThat(SeaborneTurtlesMiningSpeed.fatigueFactor(9D)).isCloseTo(8.1E-4D, offset(1.0E-12D));
  }

  @Test
  void fullCounterLandsAtLegacyFatigueOneFloor() {
    double bonus = SeaborneTurtlesMiningSpeed.fatigueCounterBonus(0.65D, 2, 2);
    double fatigueFactor = 0.0027D;
    assertThat((0.2D + bonus) * fatigueFactor).isCloseTo(0.2D * 0.3D, offset(0.000001D));
  }

  @Test
  void partialTrimMatchesLegacyReducedAmplifierRate() {
    double partial = SeaborneTurtlesMiningSpeed.fatigueCounterBonus(0.025D, 1, 2);
    double reducedFactor = Math.sqrt(0.09D * 0.0027D);
    assertThat((0.2D + partial) * 0.0027D).isCloseTo(0.2D * reducedFactor, offset(0.000001D));
    assertThat(partial).isLessThan(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(0.65D, 2, 2));
  }

  @Test
  void fatigueOneIsNeverCountered() {
    assertThat(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(0.65D, 2, 0)).isZero();
    assertThat(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(1.0D, 5, 0)).isZero();
  }

  @Test
  void counterCapsAtVanillaFatigueCeiling() {
    assertThat(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(1.0D, 5, 9))
        .isEqualTo(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(1.0D, 5, 3));
  }

  @Test
  void partialTrimAboveVanillaPenaltyCeilingIsInert() {
    assertThat(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(0.05D, 2, 9)).isZero();
  }

  @Test
  void zeroTrimIntentYieldsNoCounter() {
    assertThat(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(0D, 2, 2)).isZero();
    assertThat(SeaborneTurtlesMiningSpeed.fatigueCounterBonus(0.5D, 0, 2)).isZero();
  }

  @Test
  void durationClampsMirrorLegacyFatigueRewrite() {
    assertThat(SeaborneTurtlesMiningSpeed.counterDurationTicks(600, 80)).isEqualTo(80L);
    assertThat(SeaborneTurtlesMiningSpeed.counterDurationTicks(10, 80)).isEqualTo(20L);
    assertThat(SeaborneTurtlesMiningSpeed.counterDurationTicks(600, 5)).isEqualTo(20L);
    assertThat(SeaborneTurtlesMiningSpeed.counterDurationTicks(-1, 80)).isEqualTo(80L);
    assertThat(SeaborneTurtlesMiningSpeed.counterDurationTicks(600, 0)).isZero();
    assertThat(SeaborneTurtlesMiningSpeed.counterDurationTicks(600, -4)).isZero();
    assertThat(SeaborneTurtlesMiningSpeed.counterDurationTicks(-1, Integer.MAX_VALUE)).isEqualTo(1200L);
  }
}
