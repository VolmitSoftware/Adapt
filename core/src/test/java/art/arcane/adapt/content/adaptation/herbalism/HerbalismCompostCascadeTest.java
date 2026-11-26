package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HerbalismCompostCascadeTest {
  @Test
  void budgetSplitsFortyFortyTwentyWithTheRemainderOnDrops() {
    HerbalismCompostCascade.CompostBudget budget = HerbalismCompostCascade.splitBudget(100);

    assertThat(budget.drops()).isEqualTo(40);
    assertThat(budget.growth()).isEqualTo(40);
    assertThat(budget.inventory()).isEqualTo(20);
  }

  @Test
  void budgetAlwaysSumsBackToTheItemLimit() {
    for (int maxItems : new int[]{1, 2, 3, 7, 80, 123, 320, 999}) {
      HerbalismCompostCascade.CompostBudget budget = HerbalismCompostCascade.splitBudget(maxItems);
      assertThat(budget.drops() + budget.growth() + budget.inventory()).isEqualTo(maxItems);
      assertThat(budget.drops()).isGreaterThanOrEqualTo(budget.growth());
    }
  }

  @Test
  void budgetNeverGoesNegativeForNonsenseLimits() {
    HerbalismCompostCascade.CompostBudget budget = HerbalismCompostCascade.splitBudget(0);

    assertThat(budget.drops()).isEqualTo(1);
    assertThat(budget.growth()).isZero();
    assertThat(budget.inventory()).isZero();
    assertThat(HerbalismCompostCascade.splitBudget(-50).drops()).isEqualTo(1);
  }

  @Test
  void overflowFillsConvertIntoExtraBoneMeal() {
    assertThat(HerbalismCompostCascade.overflowBoneMeal(0, 4)).isZero();
    assertThat(HerbalismCompostCascade.overflowBoneMeal(3, 4)).isZero();
    assertThat(HerbalismCompostCascade.overflowBoneMeal(4, 4)).isEqualTo(1);
    assertThat(HerbalismCompostCascade.overflowBoneMeal(11, 4)).isEqualTo(2);
    assertThat(HerbalismCompostCascade.overflowBoneMeal(120, 4)).isEqualTo(30);
  }

  @Test
  void overflowBoneMealRejectsInvalidRatios() {
    assertThat(HerbalismCompostCascade.overflowBoneMeal(40, 0)).isZero();
    assertThat(HerbalismCompostCascade.overflowBoneMeal(40, -4)).isZero();
    assertThat(HerbalismCompostCascade.overflowBoneMeal(-40, 4)).isZero();
  }

  @Test
  void maturationAttemptsAreBoundedByBothConfigAndCompostProgress() {
    assertThat(HerbalismCompostCascade.maturationAttempts(12, 30)).isEqualTo(12);
    assertThat(HerbalismCompostCascade.maturationAttempts(12, 5)).isEqualTo(5);
    assertThat(HerbalismCompostCascade.maturationAttempts(12, 0)).isZero();
    assertThat(HerbalismCompostCascade.maturationAttempts(0, 30)).isZero();
    assertThat(HerbalismCompostCascade.maturationAttempts(-3, 30)).isZero();
  }

  @Test
  void compostProcessingChargesOnlyTheRemainingPhaseBudget() {
    assertThat(HerbalismCompostCascade.compostProcessCount(64, 40, 39)).isEqualTo(1);
    assertThat(HerbalismCompostCascade.compostProcessCount(3, 40, 12)).isEqualTo(3);
    assertThat(HerbalismCompostCascade.compostProcessCount(64, 40, 40)).isZero();
    assertThat(HerbalismCompostCascade.compostProcessCount(64, 40, 45)).isZero();
  }

  @Test
  void leafCommitRejectsAListenerReplacement() {
    assertThat(HerbalismCompostCascade.matchesLeafCommit(Id.minecraft("oak_leaves"), Id.minecraft("oak_leaves")))
        .isTrue();
    assertThat(HerbalismCompostCascade.matchesLeafCommit(Id.minecraft("oak_leaves"), Id.minecraft("stone")))
        .isFalse();
    assertThat(HerbalismCompostCascade.matchesLeafCommit(Id.minecraft("stone"), Id.minecraft("stone")))
        .isFalse();
  }

  @Test
  void cropCommitRejectsAListenerTypeOrAgeChange() {
    BlockStateRef crop = mock(BlockStateRef.class);
    when(crop.age()).thenReturn(7);
    when(crop.maxAge()).thenReturn(7);

    assertThat(HerbalismCompostCascade.matchesCropCommit(
        Id.minecraft("wheat"), Id.minecraft("wheat"), crop, true)).isTrue();
    assertThat(HerbalismCompostCascade.matchesCropCommit(
        Id.minecraft("wheat"), Id.minecraft("carrots"), crop, true)).isFalse();
    assertThat(HerbalismCompostCascade.matchesCropCommit(
        Id.minecraft("wheat"), Id.minecraft("wheat"), crop, false)).isFalse();

    when(crop.age()).thenReturn(3);
    assertThat(HerbalismCompostCascade.matchesCropCommit(
        Id.minecraft("wheat"), Id.minecraft("wheat"), crop, false)).isTrue();
    assertThat(HerbalismCompostCascade.matchesCropCommit(
        Id.minecraft("wheat"), Id.minecraft("wheat"), crop, true)).isFalse();
  }

  @Test
  void aLentCascadeRollsValuablesOnlyForTheShareThatDidNotComeFromForcedCrops() {
    assertThat(HerbalismCompostCascade.valuableShare(10, 10)).isZero();
    assertThat(HerbalismCompostCascade.valuableShare(10, 0)).isEqualTo(1D);
    assertThat(HerbalismCompostCascade.valuableShare(10, 4)).isEqualTo(0.6D);
    assertThat(HerbalismCompostCascade.valuableShare(0, 0)).isZero();
  }
}
