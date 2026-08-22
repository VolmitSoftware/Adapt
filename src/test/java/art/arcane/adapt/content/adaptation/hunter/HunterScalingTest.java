package art.arcane.adapt.content.adaptation.hunter;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HunterScalingTest {
  @Test
  void predatorRampBuildsOnSameTargetWithinWindowAndCapsOut() {
    int stacks = HunterPredatorFocus.nextStacks(0, false, false, 5);
    assertThat(stacks).isEqualTo(1);

    stacks = HunterPredatorFocus.nextStacks(stacks, true, true, 5);
    assertThat(stacks).isEqualTo(2);

    stacks = HunterPredatorFocus.nextStacks(stacks, true, true, 5);
    assertThat(stacks).isEqualTo(3);

    for (int i = 0; i < 10; i++) {
      stacks = HunterPredatorFocus.nextStacks(stacks, true, true, 5);
    }
    assertThat(stacks).isEqualTo(5);
  }

  @Test
  void predatorRampResetsOnTargetSwitchOrTimeout() {
    assertThat(HunterPredatorFocus.nextStacks(4, false, true, 5)).isEqualTo(1);
    assertThat(HunterPredatorFocus.nextStacks(4, true, false, 5)).isEqualTo(1);
  }

  @Test
  void predatorMultiplierIsNeutralOnFirstHitAndGrowsWithStacks() {
    assertThat(HunterPredatorFocus.damageMultiplier(1, 0.07)).isEqualTo(1.0);
    assertThat(HunterPredatorFocus.damageMultiplier(4, 0.07)).isEqualTo(1.0 + (3 * 0.07));
    assertThat(HunterPredatorFocus.damageMultiplier(4, 0.07))
        .isGreaterThan(HunterPredatorFocus.damageMultiplier(2, 0.07));
  }

  @Test
  void bigGameOnlyClassifiesLargeAndBossMobs() {
    assertThat(HunterBigGameHunter.isBigGame(EntityType.RAVAGER)).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(EntityType.IRON_GOLEM)).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(EntityType.WARDEN)).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(EntityType.WITHER)).isTrue();
    assertThat(HunterBigGameHunter.isBigGame(EntityType.ZOMBIE)).isFalse();
    assertThat(HunterBigGameHunter.isBigGame(EntityType.CHICKEN)).isFalse();
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
    assertThat(HunterBloodTrail.isWounded(9.0, 20.0, 0.5)).isTrue();
    assertThat(HunterBloodTrail.isWounded(11.0, 20.0, 0.5)).isFalse();
    assertThat(HunterBloodTrail.isWounded(0.0, 20.0, 0.5)).isFalse();
    assertThat(HunterBloodTrail.isWounded(-3.0, 20.0, 0.5)).isFalse();
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
