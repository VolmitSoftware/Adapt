package art.arcane.adapt.content.adaptation.blocking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingAdaptationConfigDefaultsTest {
  @Test
  void shieldWallDefaultsAreFeltAtLevelOneAndImproveWithLevel() {
    BlockingShieldWall.Config c = new BlockingShieldWall.Config();
    assertThat(c.rangeBase).isGreaterThan(0);
    assertThat(c.rangeFactor).isGreaterThan(0);
    assertThat(c.arcDegreesBase).isGreaterThan(0);
    assertThat(c.arcDegreesFactor).isGreaterThan(0);
    assertThat(c.damageReductionBase).isBetween(0.0001, c.maxDamageReduction);
    assertThat(c.damageReductionFactor).isGreaterThan(0);
    assertThat(c.maxDamageReduction).isGreaterThan(c.damageReductionBase).isLessThanOrEqualTo(1.0);
    assertThat(c.xpPerDamageShielded).isGreaterThan(0);
  }

  @Test
  void perfectGuardWindowWidensWithLevelAndStaggerIsNonZero() {
    BlockingPerfectGuard.Config c = new BlockingPerfectGuard.Config();
    assertThat(c.windowMillisBase).isGreaterThan(0);
    assertThat(c.windowMillisFactor).isGreaterThan(0);
    assertThat(c.staggerTicksBase).isGreaterThan(0);
    assertThat(c.staggerTicksFactor).isGreaterThan(0);
    assertThat(c.staggerAmplifierFactor).isGreaterThan(0);
    assertThat(c.staggerKnockback).isGreaterThan(0);
    assertThat(c.cooldownMillis).isGreaterThan(0);
    assertThat(c.xpOnNegate).isGreaterThan(0);
  }

  @Test
  void temperedGuardChanceAndAmountAreNonZeroAndClamped() {
    BlockingTemperedGuard.Config c = new BlockingTemperedGuard.Config();
    assertThat(c.repairChanceBase).isBetween(0.0001, c.maxRepairChance);
    assertThat(c.repairChanceFactor).isGreaterThan(0);
    assertThat(c.maxRepairChance).isGreaterThan(c.repairChanceBase).isLessThanOrEqualTo(1.0);
    assertThat(c.repairAmountBase).isGreaterThanOrEqualTo(1);
    assertThat(c.repairAmountFactor).isGreaterThan(0);
    assertThat(c.xpPerDurabilityRepaired).isGreaterThan(0);
  }

  @Test
  void resolveRecoveryAndResistanceScaleUpward() {
    BlockingShieldbearersResolve.Config c = new BlockingShieldbearersResolve.Config();
    assertThat(c.recoverySpeedBase).isBetween(0.0001, c.maxRecoverySpeed);
    assertThat(c.recoverySpeedFactor).isGreaterThan(0);
    assertThat(c.maxRecoverySpeed).isGreaterThan(c.recoverySpeedBase).isLessThan(1.0);
    assertThat(c.resistanceAmplifierFactor).isGreaterThan(0);
    assertThat(c.minResistanceTicks).isGreaterThan(0);
    assertThat(c.minCooldownTicks).isGreaterThan(0);
    assertThat(c.reprocessGuardMillis).isGreaterThan(0);
    assertThat(c.xpOnResolve).isGreaterThan(0);
  }

  @Test
  void phalanxCrafterUnlocksTwoRecipeTiers() {
    BlockingPhalanxCrafter.Config c = new BlockingPhalanxCrafter.Config();
    assertThat(c.maxLevel).isEqualTo(2);
    assertThat(c.baseCost).isGreaterThan(0);
    assertThat(c.initialCost).isGreaterThan(0);
  }

  @Test
  void interposeShareAndRangeScaleUpwardWithHungerCost() {
    BlockingInterpose.Config c = new BlockingInterpose.Config();
    assertThat(c.redirectShareBase).isBetween(0.0001, c.maxRedirectShare);
    assertThat(c.redirectShareFactor).isGreaterThan(0);
    assertThat(c.maxRedirectShare).isGreaterThan(c.redirectShareBase).isLessThanOrEqualTo(1.0);
    assertThat(c.rangeBase).isGreaterThan(0);
    assertThat(c.rangeFactor).isGreaterThan(0);
    assertThat(c.lowHealthThreshold).isBetween(0.0001, 1.0);
    assertThat(c.durabilityPerDamage).isGreaterThan(0);
    assertThat(c.exhaustionPerRedirect).isGreaterThan(0);
    assertThat(c.xpPerDamageRedirected).isGreaterThan(0);
  }
}
