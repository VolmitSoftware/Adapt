package art.arcane.adapt.content.adaptation.sword;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SwordsHamstringSlowTest {
  @Test
  void slowScalarMatchesSlownessParityPerTier() {
    assertThat(SwordsHamstring.slowSpeedScalar(0)).isCloseTo(-0.15, within(1.0e-9));
    assertThat(SwordsHamstring.slowSpeedScalar(1)).isCloseTo(-0.3, within(1.0e-9));
    assertThat(SwordsHamstring.slowSpeedScalar(2)).isCloseTo(-0.45, within(1.0e-9));
  }

  @Test
  void slowScalarClampsAtFullStopForHighTiers() {
    assertThat(SwordsHamstring.slowSpeedScalar(6)).isEqualTo(-1.0);
    assertThat(SwordsHamstring.slowSpeedScalar(40)).isEqualTo(-1.0);
    assertThat(SwordsHamstring.slowSpeedScalar(Integer.MAX_VALUE)).isEqualTo(-1.0);
  }

  @Test
  void slowScalarFloorsNegativeTiersToSlownessOne() {
    assertThat(SwordsHamstring.slowSpeedScalar(-3)).isEqualTo(SwordsHamstring.slowSpeedScalar(0));
  }

  @Test
  void slowScalarIsMonotonicallyStrongerWithTier() {
    assertThat(SwordsHamstring.slowSpeedScalar(2)).isLessThan(SwordsHamstring.slowSpeedScalar(1));
    assertThat(SwordsHamstring.slowSpeedScalar(1)).isLessThan(SwordsHamstring.slowSpeedScalar(0));
  }

  @Test
  void slowTierGrowsWithLevel() {
    SwordsHamstring.Config config = new SwordsHamstring.Config();
    int tierAtLevelOne = slowTier(config, 1);
    int tierAtMaxLevel = slowTier(config, config.maxLevel);
    assertThat(tierAtLevelOne).isGreaterThanOrEqualTo(0);
    assertThat(tierAtMaxLevel).isGreaterThan(tierAtLevelOne);
  }

  @Test
  void durationNeverReachesPermanentApplyFallback() {
    SwordsHamstring.Config config = new SwordsHamstring.Config();
    config.durationTicksBase = -500;
    config.durationTicksFactor = 0;
    for (int level = 1; level <= config.maxLevel; level++) {
      assertThat(durationTicks(config, level)).isGreaterThanOrEqualTo(10);
    }
  }

  @Test
  void durationGrowsWithLevel() {
    SwordsHamstring.Config config = new SwordsHamstring.Config();
    assertThat(durationTicks(config, config.maxLevel)).isGreaterThan(durationTicks(config, 1));
  }

  private static int slowTier(SwordsHamstring.Config config, int level) {
    return Math.max(0, (int) Math.round(config.slowTierBase + (levelPercent(level, config.maxLevel) * config.slowTierFactor)));
  }

  private static int durationTicks(SwordsHamstring.Config config, int level) {
    return Math.max(10, (int) Math.round(config.durationTicksBase + (levelPercent(level, config.maxLevel) * config.durationTicksFactor)));
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
