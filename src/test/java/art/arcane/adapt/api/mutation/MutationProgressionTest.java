package art.arcane.adapt.api.mutation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MutationProgressionTest {
  private final MutationProgression progression = new MutationProgression(25, 50, 200, true);

  @Test
  void slotBoundariesUseCurrentMasterLevel() {
    assertThat(progression.unlockedSlotCount(24)).isZero();
    assertThat(progression.isSlotUnlocked(25, 1)).isTrue();
    assertThat(progression.unlockedSlotCount(49)).isEqualTo(1);
    assertThat(progression.isSlotUnlocked(50, 2)).isTrue();
  }

  @Test
  void perfectAdaptationRegressesWhenCurrentLevelFalls() {
    assertThat(progression.isPerfect(199)).isFalse();
    assertThat(progression.isBurdenActive(199)).isTrue();
    assertThat(progression.isPerfect(200)).isTrue();
    assertThat(progression.isPerfect(199)).isFalse();
  }

  @Test
  void perfectAdaptationCanBeDisabledWithoutChangingSlots() {
    MutationProgression disabled = new MutationProgression(25, 50, 200, false);

    assertThat(disabled.unlockedSlotCount(200)).isEqualTo(2);
    assertThat(disabled.isPerfect(200)).isFalse();
  }
}
