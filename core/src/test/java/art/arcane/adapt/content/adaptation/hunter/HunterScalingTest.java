package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HunterScalingTest {
  @Test
  void predatorRampBuildsOnSameTargetWithinWindowAndCapsOut() {
    int stacks = HunterRamp.nextStacks(0, false, false, 5, 1);
    assertThat(stacks).isEqualTo(1);

    stacks = HunterRamp.nextStacks(stacks, true, true, 5, 1);
    assertThat(stacks).isEqualTo(2);

    stacks = HunterRamp.nextStacks(stacks, true, true, 5, 1);
    assertThat(stacks).isEqualTo(3);

    for (int i = 0; i < 10; i++) {
      stacks = HunterRamp.nextStacks(stacks, true, true, 5, 1);
    }
    assertThat(stacks).isEqualTo(5);
  }

  @Test
  void predatorRampResetsOnTargetSwitchOrTimeout() {
    assertThat(HunterRamp.nextStacks(4, false, true, 5, 1)).isEqualTo(1);
    assertThat(HunterRamp.nextStacks(4, true, false, 5, 1)).isEqualTo(1);
  }

  @Test
  void predatorMultiplierIsNeutralOnFirstHitAndGrowsWithStacks() {
    assertThat(HunterRamp.damageMultiplier(1, 0.07)).isEqualTo(1.0);
    assertThat(HunterRamp.damageMultiplier(4, 0.07)).isEqualTo(1.0 + (3 * 0.07));
    assertThat(HunterRamp.damageMultiplier(4, 0.07))
        .isGreaterThan(HunterRamp.damageMultiplier(2, 0.07));
  }

  @Test
  void bigGameOnlyClassifiesLargeAndBossMobs() {
    assertThat(HunterBigGameHunter.isBigGame(Id.minecraft("ravager"))).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(Id.minecraft("iron_golem"))).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(Id.minecraft("warden"))).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(Id.minecraft("wither"))).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(Id.minecraft("zombie"))).isFalse();
    assertThat(HunterBigGameHunter.isBigGame(Id.minecraft("chicken"))).isFalse();
  }

  @Test
  void bigGameScalingIncreasesWithLevelAndDropChanceIsCapped() {
    double lowDamage = HunterBigGameHunter.bonusDamage(0.2, 0.15, 0.45);
    double highDamage = HunterBigGameHunter.bonusDamage(1.0, 0.15, 0.45);
    assertThat(highDamage).isGreaterThan(lowDamage);

    double chance = HunterBigGameHunter.extraDropChance(1.0, 0.15, 0.45, 0.4);
    assertThat(chance).isEqualTo(0.4);
    assertThat(HunterBigGameHunter.extraDropChance(0.0, 0.15, 0.45, 0.75)).isEqualTo(0.15);
  }

  @Test
  void bloodTrailWoundRequiresSurvivingBelowFraction() {
    assertThat(HunterWounds.isWounded(9.0, 20.0, 0.5)).isTrue();
    assertThat(HunterWounds.isWounded(11.0, 20.0, 0.5)).isFalse();
    assertThat(HunterWounds.isWounded(0.0, 20.0, 0.5)).isFalse();
    assertThat(HunterWounds.isWounded(-3.0, 20.0, 0.5)).isFalse();
  }

  @Test
  void bloodTrailDurationAndRangeGrowWithLevel() {
    assertThat(HunterBloodTrail.trailDurationTicks(1.0, 100, 200))
        .isGreaterThan(HunterBloodTrail.trailDurationTicks(0.2, 100, 200));
    assertThat(HunterBloodTrail.trackingRange(1.0, 16, 32))
        .isGreaterThan(HunterBloodTrail.trackingRange(0.2, 16, 32));
  }

  @Test
  void snareScalingIsMonotonicAndNeverBelowOne() {
    assertThat(HunterSnareLine.scaleInt(1.0, 3, 5)).isGreaterThan(HunterSnareLine.scaleInt(0.2, 3, 5));
    assertThat(HunterSnareLine.scaleInt(0.0, 0, 0)).isEqualTo(1);
  }
}
