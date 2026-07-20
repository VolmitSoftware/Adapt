package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityMovementAdaptationConfigTest {
  private static double scaled(double base, double factor, int level, int maxLevel) {
    double percent = Math.min(1D, Math.max(0D, (double) level / maxLevel));
    return base + (percent * factor);
  }

  @Test
  void slipstreamDefaultsAreSaneAndLevelingImprovesForceAndCooldown() {
    AgilitySlipstreamSlide.Config c = new AgilitySlipstreamSlide.Config();
    assertThat(c.slideForceBase).isGreaterThan(0D);
    assertThat(c.slideForceFactor).isGreaterThan(0D);
    assertThat(c.hungerCost).isGreaterThan(0D);
    assertThat(c.cooldownMillisFloor).isLessThan((long) c.cooldownMillisBase);

    double forceL1 = scaled(c.slideForceBase, c.slideForceFactor, 1, c.maxLevel);
    double forceMax = scaled(c.slideForceBase, c.slideForceFactor, c.maxLevel, c.maxLevel);
    assertThat(forceMax).isGreaterThan(forceL1);

    long cooldownL1 = Math.max(c.cooldownMillisFloor,
        Math.round(c.cooldownMillisBase - scaled(0, c.cooldownMillisReduction, 1, c.maxLevel)));
    long cooldownMax = Math.max(c.cooldownMillisFloor,
        Math.round(c.cooldownMillisBase - scaled(0, c.cooldownMillisReduction, c.maxLevel, c.maxLevel)));
    assertThat(cooldownMax).isLessThan(cooldownL1);
  }

  @Test
  void airDashDefaultsGrantASecondChargeAtMaxAndStrongerForce() {
    AgilityAirDash.Config c = new AgilityAirDash.Config();
    assertThat(c.dashForceBase).isGreaterThan(0D);
    assertThat(c.dashForceFactor).isGreaterThan(0D);
    assertThat(c.hungerCost).isGreaterThan(0D);
    assertThat(c.maxLevelCharges).isGreaterThanOrEqualTo(2);

    double forceL1 = scaled(c.dashForceBase, c.dashForceFactor, 1, c.maxLevel);
    double forceMax = scaled(c.dashForceBase, c.dashForceFactor, c.maxLevel, c.maxLevel);
    assertThat(forceMax).isGreaterThan(forceL1);
  }

  @Test
  void catReflexesDodgeChanceIsCappedAndClimbs() {
    AgilityCatReflexes.Config c = new AgilityCatReflexes.Config();
    assertThat(c.dodgeChanceBase).isGreaterThan(0D);
    assertThat(c.maxDodgeChance).isGreaterThan(0D).isLessThanOrEqualTo(1D);
    assertThat(c.dodgeChanceBase).isLessThan(c.maxDodgeChance);

    double chanceL1 = Math.min(c.maxDodgeChance, scaled(c.dodgeChanceBase, c.dodgeChanceFactor, 1, c.maxLevel));
    double chanceMax = Math.min(c.maxDodgeChance, scaled(c.dodgeChanceBase, c.dodgeChanceFactor, c.maxLevel, c.maxLevel));
    assertThat(chanceMax).isGreaterThanOrEqualTo(chanceL1);
    assertThat(chanceMax).isLessThanOrEqualTo(c.maxDodgeChance);
  }

  @Test
  void featherfootUnlocksSurfacesInAscendingLevelOrder() {
    AgilityFeatherfoot.Config c = new AgilityFeatherfoot.Config();
    assertThat(c.farmlandMinLevel).isGreaterThanOrEqualTo(1);
    assertThat(c.farmlandMinLevel).isLessThan(c.pressurePlateMinLevel);
    assertThat(c.pressurePlateMinLevel).isLessThan(c.berryBushMinLevel);
    assertThat(c.berryBushMinLevel).isLessThan(c.powderSnowMinLevel);
    assertThat(c.powderSnowMinLevel).isLessThanOrEqualTo(c.maxLevel);
  }

  @Test
  void vaultIsASingleLevelFenceJumpWithoutScalingOrHungerConfiguration() {
    AgilityVault.Config c = new AgilityVault.Config();
    assertThat(c.maxLevel).isEqualTo(1);
    assertThat(c.costFactor).isZero();
    assertThat(c.jumpHeight).isGreaterThan(1.5D);
    assertThat(c.xpPerVault).isGreaterThanOrEqualTo(0D);
  }

  @Test
  void marathonerDrainReductionStaysWithinCap() {
    AgilityMarathoner.Config c = new AgilityMarathoner.Config();
    assertThat(c.drainReductionBase).isGreaterThan(0D);
    assertThat(c.drainReductionFactor).isGreaterThan(0D);
    assertThat(c.maxDrainReduction).isGreaterThan(0D).isLessThanOrEqualTo(1D);
    assertThat(c.drainReductionBase).isLessThan(c.maxDrainReduction);

    double reductionMax = Math.min(c.maxDrainReduction,
        scaled(c.drainReductionBase, c.drainReductionFactor, c.maxLevel, c.maxLevel));
    assertThat(reductionMax).isLessThanOrEqualTo(c.maxDrainReduction);
    assertThat(reductionMax).isGreaterThan(c.drainReductionBase);
  }

  @Test
  void kipUpWidensWindowAndSpeedWithLevels() {
    AgilityKipUp.Config c = new AgilityKipUp.Config();
    assertThat(c.recoveryWindowMillisBase).isGreaterThan(0D);
    assertThat(c.recoveryWindowMillisFactor).isGreaterThan(0D);
    assertThat(c.speedDurationTicks).isGreaterThan(0);
    assertThat(c.recoverySpeed).isGreaterThan(0D);

    long windowL1 = Math.round(scaled(c.recoveryWindowMillisBase, c.recoveryWindowMillisFactor, 1, c.maxLevel));
    long windowMax = Math.round(scaled(c.recoveryWindowMillisBase, c.recoveryWindowMillisFactor, c.maxLevel, c.maxLevel));
    assertThat(windowMax).isGreaterThan(windowL1);

    int ampL1 = (int) Math.round(scaled(c.speedAmplifierBase, c.speedAmplifierFactor, 1, c.maxLevel));
    int ampMax = (int) Math.round(scaled(c.speedAmplifierBase, c.speedAmplifierFactor, c.maxLevel, c.maxLevel));
    assertThat(ampMax).isGreaterThanOrEqualTo(ampL1);
  }
}
