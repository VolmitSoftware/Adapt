package art.arcane.adapt.content.adaptation.sword;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SwordsWhetstoneRitualTest {
  @Test
  void sharpnessDamageMatchesStrengthParityPerTier() {
    assertThat(SwordsWhetstoneRitual.sharpnessDamage(0)).isCloseTo(3.0D, within(1.0E-9));
    assertThat(SwordsWhetstoneRitual.sharpnessDamage(1)).isCloseTo(6.0D, within(1.0E-9));
    assertThat(SwordsWhetstoneRitual.sharpnessDamage(2)).isCloseTo(9.0D, within(1.0E-9));
  }

  @Test
  void buffAmplifierScalesWithLevelPercent() {
    assertThat(SwordsWhetstoneRitual.buffAmplifier(0D, 2D, 0.2D)).isEqualTo(0);
    assertThat(SwordsWhetstoneRitual.buffAmplifier(0D, 2D, 0.4D)).isEqualTo(1);
    assertThat(SwordsWhetstoneRitual.buffAmplifier(0D, 2D, 1.0D)).isEqualTo(2);
  }

  @Test
  void buffAmplifierClampsAtZero() {
    assertThat(SwordsWhetstoneRitual.buffAmplifier(-5D, 2D, 0.0D)).isEqualTo(0);
    assertThat(SwordsWhetstoneRitual.buffAmplifier(-5D, 2D, 1.0D)).isEqualTo(0);
  }

  @Test
  void buffDurationTicksClampsToPositiveFloor() {
    assertThat(SwordsWhetstoneRitual.buffDurationTicks(0D, 0D, 0.0D)).isEqualTo(40);
    assertThat(SwordsWhetstoneRitual.buffDurationTicks(-500D, 0D, 1.0D)).isEqualTo(40);
    assertThat(SwordsWhetstoneRitual.buffDurationTicks(200D, 400D, 1.0D)).isEqualTo(600);
    assertThat(SwordsWhetstoneRitual.buffDurationTicks(-1.0E6D, -1.0E6D, 1.0D)).isPositive();
  }

  @Test
  void defaultConfigMirrorsLegacyStrengthPotion() {
    SwordsWhetstoneRitual.Config config = new SwordsWhetstoneRitual.Config();
    double percentAtLevelOne = levelPercent(1, config.maxLevel);
    double percentAtMaxLevel = levelPercent(config.maxLevel, config.maxLevel);

    int amplifierAtLevelOne = SwordsWhetstoneRitual.buffAmplifier(config.strengthBase, config.strengthFactor, percentAtLevelOne);
    int amplifierAtMaxLevel = SwordsWhetstoneRitual.buffAmplifier(config.strengthBase, config.strengthFactor, percentAtMaxLevel);
    assertThat(SwordsWhetstoneRitual.sharpnessDamage(amplifierAtLevelOne)).isCloseTo(3.0D, within(1.0E-9));
    assertThat(SwordsWhetstoneRitual.sharpnessDamage(amplifierAtMaxLevel)).isCloseTo(9.0D, within(1.0E-9));

    assertThat(SwordsWhetstoneRitual.buffDurationTicks(config.durationTicksBase, config.durationTicksFactor, percentAtLevelOne)).isEqualTo(280);
    assertThat(SwordsWhetstoneRitual.buffDurationTicks(config.durationTicksBase, config.durationTicksFactor, percentAtMaxLevel)).isEqualTo(600);
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
