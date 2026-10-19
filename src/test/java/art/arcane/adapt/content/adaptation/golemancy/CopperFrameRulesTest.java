package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class CopperFrameRulesTest {
  private static final List<Integer> HEALTH = List.of(4, 8, 12, 16, 20);
  private static final List<Integer> CARRY = List.of(16, 32, 48, 64, 128);
  private static final List<Double> SPEED = List.of(0D, 0.05D, 0.10D, 0.15D, 0.20D);
  private static final Patina PATINA = new Patina(2, 0.05D, 3, 5);

  @Test
  void carryFollowsTheLevelTable() {
    assertThat(CopperFrameRules.carry(CARRY, 0)).isZero();
    assertThat(CopperFrameRules.carry(CARRY, 1)).isEqualTo(16);
    assertThat(CopperFrameRules.carry(CARRY, 4)).isEqualTo(64);
    assertThat(CopperFrameRules.carry(CARRY, 5)).isEqualTo(128);
    assertThat(CopperFrameRules.carry(CARRY, 9)).isEqualTo(128);
    assertThat(CopperFrameRules.carry(List.of(-4), 1)).isZero();
    assertThat(CopperFrameRules.carry(Arrays.asList(16, null), 2)).isZero();
  }

  @Test
  void theFrameAddsHealthAndSpeedByLevel() {
    assertThat(CopperFrameRules.frameDeltas(HEALTH, SPEED, 0)).isEmpty();
    assertThat(CopperFrameRules.frameDeltas(HEALTH, SPEED, 1)).containsExactly(GolemStatDelta.add(GolemStat.MAX_HEALTH, 4D));
    assertThat(CopperFrameRules.frameDeltas(HEALTH, SPEED, 3))
        .containsExactly(GolemStatDelta.add(GolemStat.MAX_HEALTH, 12D), GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, 0.10D));
    assertThat(CopperFrameRules.frameDeltas(HEALTH, SPEED, 5))
        .containsExactly(GolemStatDelta.add(GolemStat.MAX_HEALTH, 20D), GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, 0.20D));
    assertThat(CopperFrameRules.frameDeltas(HEALTH, List.of(Double.NaN), 1)).containsExactly(GolemStatDelta.add(GolemStat.MAX_HEALTH, 4D));
  }

  @Test
  void patinaArmorGrowsPerStageAndDoublesAtLevelThree() {
    int[][] expected = {
        {0, 0, 0, 0},
        {0, 2, 4, 6},
        {0, 2, 4, 6},
        {0, 4, 8, 12},
        {0, 4, 8, 12},
        {0, 4, 8, 12}};
    for (int level = 0; level <= 5; level++) {
      for (int stage = 0; stage <= 3; stage++) {
        assertThat(CopperFrameRules.patinaArmor(stage, level, PATINA.armor(), PATINA.doubleLevel()))
            .as("armor at level %s stage %s", level, stage).isEqualTo(expected[level][stage]);
      }
    }
  }

  @Test
  void patinaSlowsFivePercentPerStageUntilLevelFive() {
    double[][] expected = {
        {0, 0, 0, 0},
        {0, -0.05, -0.10, -0.15},
        {0, -0.05, -0.10, -0.15},
        {0, -0.05, -0.10, -0.15},
        {0, -0.05, -0.10, -0.15},
        {0, 0, 0, 0}};
    for (int level = 0; level <= 5; level++) {
      for (int stage = 0; stage <= 3; stage++) {
        assertThat(CopperFrameRules.patinaSpeed(stage, level, PATINA.penalty(), PATINA.removedLevel()))
            .as("speed at level %s stage %s", level, stage).isCloseTo(expected[level][stage], offset(1.0E-9));
      }
    }
  }

  @Test
  void patinaDeltasCarryArmorAndTheSpeedPenaltyAndClampOddInput() {
    assertThat(deltas(0, 5)).isEmpty();
    assertThat(deltas(3, 0)).isEmpty();
    assertThat(deltas(1, 1)).containsExactly(GolemStatDelta.add(GolemStat.ARMOR, 2D), GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, -0.05D));
    assertThat(deltas(2, 3)).containsExactly(GolemStatDelta.add(GolemStat.ARMOR, 8D), GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, -0.10D));
    assertThat(deltas(3, 5)).containsExactly(GolemStatDelta.add(GolemStat.ARMOR, 12D));
    assertThat(deltas(9, 1)).isEqualTo(deltas(3, 1));
    assertThat(deltas(-2, 1)).isEmpty();
    assertThat(CopperFrameRules.patinaSpeed(3, 1, 0.9D, 5)).isEqualTo(-1D);
    assertThat(CopperFrameRules.patinaSpeed(3, 1, -0.5D, 5)).isZero();
    assertThat(CopperFrameRules.patinaArmor(3, 1, -2, 3)).isZero();
  }

  @Test
  void overdriveStartsWhenReadyAndCoolsDownFromTheStrike() {
    CopperFrameRules.Overdrive first = CopperFrameRules.overdrive(null, 0L, 60, 300);
    assertThat(first).isEqualTo(new CopperFrameRules.Overdrive(60_000L, 300_000L));

    assertThat(CopperFrameRules.overdrive(first, 100_000L, 60, 300)).isNull();
    assertThat(CopperFrameRules.overdrive(first, 299_999L, 60, 300)).isNull();
    assertThat(CopperFrameRules.overdrive(first, 300_000L, 60, 300)).isEqualTo(new CopperFrameRules.Overdrive(360_000L, 600_000L));
    assertThat(CopperFrameRules.overdrive(null, 5L, 0, -1)).isEqualTo(new CopperFrameRules.Overdrive(1_005L, 5L));
  }

  @Test
  void overdriveHalvesTheWorkIntervalOnlyWhileActive() {
    CopperFrameRules.Overdrive overdrive = new CopperFrameRules.Overdrive(60_000L, 300_000L);

    assertThat(CopperFrameRules.workIntervalMultiplier(null, 0L, 0.5D)).isEqualTo(1D);
    assertThat(CopperFrameRules.workIntervalMultiplier(overdrive, 59_999L, 0.5D)).isEqualTo(0.5D);
    assertThat(CopperFrameRules.workIntervalMultiplier(overdrive, 59_999L, 0.25D)).isEqualTo(0.25D);
    assertThat(CopperFrameRules.workIntervalMultiplier(overdrive, 59_999L, Double.NaN)).isEqualTo(1D);
    assertThat(CopperFrameRules.workIntervalMultiplier(overdrive, 59_999L, 0D)).isEqualTo(1D);
    assertThat(CopperFrameRules.workIntervalMultiplier(overdrive, 60_000L, 0.5D)).isEqualTo(1D);
    assertThat(CopperFrameRules.expired(overdrive, 299_999L)).isFalse();
    assertThat(CopperFrameRules.expired(overdrive, 300_000L)).isTrue();
    assertThat(CopperFrameRules.expired(new CopperFrameRules.Overdrive(90_000L, 30_000L), 60_000L)).isFalse();
  }

  @Test
  void lightningCountsWithinTheRadius() {
    assertThat(CopperFrameRules.withinRadius(256D, 16D)).isTrue();
    assertThat(CopperFrameRules.withinRadius(256.01D, 16D)).isFalse();
    assertThat(CopperFrameRules.withinRadius(0D, -1D)).isTrue();
    assertThat(CopperFrameRules.withinRadius(1D, Double.NaN)).isFalse();
  }

  @Test
  void theOverdriveRadiusIsClampedBetweenZeroAndSixtyFour() {
    assertThat(CopperFrameRules.overdriveRadius(16D)).isEqualTo(16D);
    assertThat(CopperFrameRules.overdriveRadius(64D)).isEqualTo(64D);
    assertThat(CopperFrameRules.overdriveRadius(500D)).isEqualTo(64D);
    assertThat(CopperFrameRules.overdriveRadius(-3D)).isZero();
    assertThat(CopperFrameRules.overdriveRadius(Double.NaN)).isEqualTo(16D);
    assertThat(CopperFrameRules.overdriveRadius(Double.POSITIVE_INFINITY)).isEqualTo(16D);
  }

  @Test
  void levelUnlocksNeverOpenBelowLevelOne() {
    assertThat(GolemancyRules.unlocked(4, 4)).isTrue();
    assertThat(GolemancyRules.unlocked(3, 4)).isFalse();
    assertThat(GolemancyRules.unlocked(0, 0)).isFalse();
  }

  private static List<GolemStatDelta> deltas(int stage, int level) {
    return CopperFrameRules.patinaDeltas(stage, level, PATINA.armor(), PATINA.penalty(), PATINA.doubleLevel(), PATINA.removedLevel());
  }

  private record Patina(int armor, double penalty, int doubleLevel, int removedLevel) {
  }
}
