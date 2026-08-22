package art.arcane.adapt.content.adaptation.blocking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingBastionStanceBraceMathTest {
  @Test
  void braceAmountPassesThroughConfiguredFraction() {
    assertThat(BlockingBastionStance.braceAmount(0.45D)).isEqualTo(0.45D);
  }

  @Test
  void braceAmountClampsNegativeReductionToZero() {
    assertThat(BlockingBastionStance.braceAmount(-0.2D)).isEqualTo(0.0D);
  }

  @Test
  void braceAmountClampsOversizedReductionToFullResistance() {
    assertThat(BlockingBastionStance.braceAmount(1.4D)).isEqualTo(1.0D);
  }

  @Test
  void defaultConfigNeverExceedsAttributeFractionRange() {
    BlockingBastionStance.Config c = new BlockingBastionStance.Config();
    double maxLevelReduction = Math.min(c.maxKnockbackReduction, c.knockbackReductionBase + c.knockbackReductionFactor);
    assertThat(BlockingBastionStance.braceAmount(maxLevelReduction)).isBetween(0.0D, 1.0D);
    assertThat(BlockingBastionStance.braceAmount(c.knockbackReductionBase)).isBetween(0.0D, 1.0D);
  }

  @Test
  void braceImpactFxMatchesLegacyRawThresholdForDampedKnockback() {
    double reduction = 0.75D;
    double rawAboveThreshold = 0.44D;
    double rawBelowThreshold = 0.36D;
    assertThat(BlockingBastionStance.braceImpactFx(rawAboveThreshold * (1.0D - reduction), reduction)).isTrue();
    assertThat(BlockingBastionStance.braceImpactFx(rawBelowThreshold * (1.0D - reduction), reduction)).isFalse();
  }

  @Test
  void braceImpactFxSkipsWhenNoReductionConfigured() {
    assertThat(BlockingBastionStance.braceImpactFx(2.0D, 0.0D)).isFalse();
    assertThat(BlockingBastionStance.braceImpactFx(2.0D, -0.5D)).isFalse();
  }

  @Test
  void braceImpactFxSkipsAtFullImmunity() {
    assertThat(BlockingBastionStance.braceImpactFx(2.0D, 1.0D)).isFalse();
    assertThat(BlockingBastionStance.braceImpactFx(2.0D, 1.5D)).isFalse();
  }
}
