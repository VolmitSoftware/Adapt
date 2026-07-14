package art.arcane.adapt.content.adaptation.crafting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class CraftingScalingTest {
  @Test
  void batchCapScalesFromBaseToBasePlusFactor() {
    assertThat(CraftingBulkArtisan.batchCap(32, 96, 0.0)).isEqualTo(32);
    assertThat(CraftingBulkArtisan.batchCap(32, 96, 1.0)).isEqualTo(128);
    assertThat(CraftingBulkArtisan.batchCap(32, 96, 0.5)).isGreaterThan(CraftingBulkArtisan.batchCap(32, 96, 0.0));
  }

  @Test
  void refundChanceIsFeltAtBaseAndClampedToMax() {
    assertThat(CraftingThriftyHands.refundChance(0.15, 0.5, 0.6, 0.0)).isCloseTo(0.15, offset(1.0E-9));
    assertThat(CraftingThriftyHands.refundChance(0.15, 0.5, 0.6, 1.0)).isCloseTo(0.6, offset(1.0E-9));
  }

  @Test
  void masterworkChanceAndBonusScaleUpWithLevel() {
    assertThat(CraftingMasterwork.masterworkChance(0.2, 0.55, 0.75, 0.0)).isCloseTo(0.2, offset(1.0E-9));
    assertThat(CraftingMasterwork.masterworkChance(0.2, 0.55, 0.75, 1.0)).isCloseTo(0.75, offset(1.0E-9));
    assertThat(CraftingMasterwork.bonusPercent(0.1, 0.4, 0.0)).isCloseTo(0.1, offset(1.0E-9));
    assertThat(CraftingMasterwork.bonusPercent(0.1, 0.4, 1.0)).isCloseTo(0.5, offset(1.0E-9));
  }

  @Test
  void preserveChanceReachesLosslessAtFullLevel() {
    assertThat(CraftingTinkerer.preserveChance(0.4, 0.6, 0.0)).isCloseTo(0.4, offset(1.0E-9));
    assertThat(CraftingTinkerer.preserveChance(0.4, 0.6, 1.0)).isCloseTo(1.0, offset(1.0E-9));
  }

  @Test
  void provisionerBonusScalesUpWithLevel() {
    assertThat(CraftingProvisioner.bonusChance(0.25, 0.5, 0.75, 0.0)).isCloseTo(0.25, offset(1.0E-9));
    assertThat(CraftingProvisioner.bonusChance(0.25, 0.5, 0.75, 1.0)).isCloseTo(0.75, offset(1.0E-9));
    assertThat(CraftingProvisioner.bonusPortions(1, 2, 0.0)).isEqualTo(1);
    assertThat(CraftingProvisioner.bonusPortions(1, 2, 1.0)).isEqualTo(3);
  }

  @Test
  void signatureAmplifierIsSlightAtLowLevelAndClampedAtMax() {
    assertThat(CraftingSignature.tradeAmplifier(0, 1, 1, 0.0)).isEqualTo(0);
    assertThat(CraftingSignature.tradeAmplifier(0, 1, 1, 1.0)).isEqualTo(1);
    assertThat(CraftingSignature.tradeAmplifier(0, 1, 1, 2.0)).isEqualTo(1);
  }

  @Test
  void craftingXpFitUsesEmptySlotsAndPartialStackRoom() {
    assertThat(CraftingXP.canFit(1, 64, 1, 0)).isTrue();
    assertThat(CraftingXP.canFit(64, 64, 1, 0)).isTrue();
    assertThat(CraftingXP.canFit(65, 64, 1, 0)).isFalse();
    assertThat(CraftingXP.canFit(64, 64, 0, 63)).isFalse();
    assertThat(CraftingXP.canFit(64, 64, 0, 64)).isTrue();
    assertThat(CraftingXP.canFit(10, 64, 0, 4)).isFalse();
    assertThat(CraftingXP.canFit(10, 64, 0, 10)).isTrue();
    assertThat(CraftingXP.canFit(2, 1, 1, 0)).isFalse();
    assertThat(CraftingXP.canFit(2, 1, 2, 0)).isTrue();
  }

  @Test
  void craftingXpFitToleratesDegenerateInputs() {
    assertThat(CraftingXP.canFit(0, 64, 0, 0)).isTrue();
    assertThat(CraftingXP.canFit(-1, 64, 0, 0)).isTrue();
    assertThat(CraftingXP.canFit(1, 0, 1, 0)).isTrue();
    assertThat(CraftingXP.canFit(1, 64, -3, -5)).isFalse();
  }

  @Test
  void compactorCoversMoreMaterialsAsLevelIncreases() {
    assertThat(CraftingCompactor.materialsCovered(1)).isEqualTo(4);
    assertThat(CraftingCompactor.materialsCovered(2)).isEqualTo(7);
    assertThat(CraftingCompactor.materialsCovered(3)).isEqualTo(11);
    assertThat(CraftingCompactor.materialsCovered(4)).isEqualTo(12);
    assertThat(CraftingCompactor.materialsCovered(1)).isLessThan(CraftingCompactor.materialsCovered(4));
  }
}
