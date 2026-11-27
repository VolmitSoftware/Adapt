package art.arcane.adapt.content.adaptation.stealth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StealthSpeedScalingTest {
  private static final double DEFAULT_MAX_SPEED_BONUS = 0.4666666666666667D;
  private static final double VANILLA_SNEAK_CAP_SCALAR = 7.0D / 3.0D;

  @Test
  void sneakSpeedBonusScalesWithLevelAndClampsNegatives() {
    assertThat(StealthSpeed.sneakSpeedBonus(0.0D, DEFAULT_MAX_SPEED_BONUS, false, 1.15D)).isEqualTo(0.0D);
    assertThat(StealthSpeed.sneakSpeedBonus(0.5D, DEFAULT_MAX_SPEED_BONUS, false, 1.15D)).isCloseTo(DEFAULT_MAX_SPEED_BONUS / 2.0D, within(1e-9));
    assertThat(StealthSpeed.sneakSpeedBonus(1.0D, DEFAULT_MAX_SPEED_BONUS, false, 1.15D)).isCloseTo(DEFAULT_MAX_SPEED_BONUS, within(1e-9));
    assertThat(StealthSpeed.sneakSpeedBonus(-0.5D, DEFAULT_MAX_SPEED_BONUS, false, 1.15D)).isEqualTo(0.0D);
  }

  @Test
  void sneakSpeedBonusFoldsCrawlMultiplierOnlyWhileCrawling() {
    assertThat(StealthSpeed.sneakSpeedBonus(1.0D, DEFAULT_MAX_SPEED_BONUS, true, 1.15D)).isCloseTo(DEFAULT_MAX_SPEED_BONUS * 1.15D, within(1e-9));
    assertThat(StealthSpeed.sneakSpeedBonus(1.0D, DEFAULT_MAX_SPEED_BONUS, true, -2.0D)).isEqualTo(0.0D);
    assertThat(StealthSpeed.sneakSpeedBonus(1.0D, DEFAULT_MAX_SPEED_BONUS, false, 5.0D)).isCloseTo(DEFAULT_MAX_SPEED_BONUS, within(1e-9));
  }

  @Test
  void sneakSpeedScalarMatchesLegacyWalkSpeedRatio() {
    assertThat(StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, 0.4D)).isCloseTo(2.0D, within(1e-6));
    assertThat(StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, 0.2D)).isCloseTo(1.0D, within(1e-6));
    assertThat(StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, 0.0D)).isEqualTo(0.0D);
    assertThat(StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, -0.3D)).isEqualTo(0.0D);
  }

  @Test
  void sneakSpeedScalarCapsAtVanillaSneakClamp() {
    assertThat(StealthSpeed.MAX_SNEAK_SCALAR).isCloseTo(VANILLA_SNEAK_CAP_SCALAR, within(1e-12));
    assertThat(StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, 0.8D)).isCloseTo(VANILLA_SNEAK_CAP_SCALAR, within(1e-6));
    assertThat(StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, 2.0D)).isCloseTo(VANILLA_SNEAK_CAP_SCALAR, within(1e-6));
    assertThat(StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, DEFAULT_MAX_SPEED_BONUS * 1.15D)).isCloseTo(VANILLA_SNEAK_CAP_SCALAR, within(1e-6));
    assertThat(StealthSpeed.sneakSpeedScalar(0.3f, 0.2f, -1f, 0.4f, 0.8D)).isCloseTo(1.0D / 3.0D, within(1e-6));
  }

  @Test
  void defaultCurveStepsProportionallyToTheVanillaCapAtMaxLevel() {
    double levelOne = StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, StealthSpeed.sneakSpeedBonus(1.0D / 3.0D, DEFAULT_MAX_SPEED_BONUS, false, 1.15D));
    double levelTwo = StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, StealthSpeed.sneakSpeedBonus(2.0D / 3.0D, DEFAULT_MAX_SPEED_BONUS, false, 1.15D));
    double levelThree = StealthSpeed.sneakSpeedScalar(0.2f, 0.2f, -1f, 1f, StealthSpeed.sneakSpeedBonus(1.0D, DEFAULT_MAX_SPEED_BONUS, false, 1.15D));

    assertThat(levelOne).isCloseTo(VANILLA_SNEAK_CAP_SCALAR / 3.0D, within(1e-6));
    assertThat(levelTwo).isCloseTo(VANILLA_SNEAK_CAP_SCALAR * 2.0D / 3.0D, within(1e-6));
    assertThat(levelThree).isCloseTo(VANILLA_SNEAK_CAP_SCALAR, within(1e-6));
    assertThat((1.0D + levelThree) * 0.3D).isCloseTo(1.0D, within(1e-6));
  }

  @Test
  void sneakSpeedScalarFallsBackToBaselineWhenWalkSpeedIsZero() {
    assertThat(StealthSpeed.sneakSpeedScalar(0f, 0.2f, -1f, 1f, 0.2D)).isCloseTo(1.0D, within(1e-6));
    assertThat(StealthSpeed.sneakSpeedScalar(0f, 0.2f, -1f, 1f, 0.8D)).isCloseTo(VANILLA_SNEAK_CAP_SCALAR, within(1e-6));
  }

  @Test
  void sneakSpeedScalarReturnsZeroForNonPositiveBase() {
    assertThat(StealthSpeed.sneakSpeedScalar(-0.5f, 0.2f, -1f, 1f, 0.8D)).isEqualTo(0.0D);
    assertThat(StealthSpeed.sneakSpeedScalar(0f, 0f, -1f, 1f, 0.8D)).isEqualTo(0.0D);
    assertThat(StealthSpeed.sneakSpeedScalar(0f, -0.2f, -1f, 1f, 0.8D)).isEqualTo(0.0D);
  }

  @Test
  void autoStepCommitsFallResetAndEffectsOnlyAfterConfirmedTeleportCompletion() {
    RuntimeException failure = new RuntimeException("failed");

    assertThat(StealthSpeed.successfulAutoStepTeleport(true, null)).isTrue();
    assertThat(StealthSpeed.successfulAutoStepTeleport(false, null)).isFalse();
    assertThat(StealthSpeed.successfulAutoStepTeleport(null, null)).isFalse();
    assertThat(StealthSpeed.successfulAutoStepTeleport(true, failure)).isFalse();
  }
}
