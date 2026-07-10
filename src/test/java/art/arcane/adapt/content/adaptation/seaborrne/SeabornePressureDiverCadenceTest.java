package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeabornePressureDiverCadenceTest {
  @Test
  void refreshCadenceStaysInsideEffectDurationBounds() {
    assertThat(SeabornePressureDiver.refreshIntervalMillis(4)).isEqualTo(250L);
    assertThat(SeabornePressureDiver.refreshIntervalMillis(60)).isEqualTo(750L);
    assertThat(SeabornePressureDiver.refreshIntervalMillis(100)).isEqualTo(750L);
  }

  @Test
  void activeCreditUsesElapsedTicksWithoutDoubleCounting() {
    assertThat(SeabornePressureDiver.elapsedActiveTicks(null, 1000L)).isEqualTo(1D);
    assertThat(SeabornePressureDiver.elapsedActiveTicks(1000L, 1000L)).isZero();
    assertThat(SeabornePressureDiver.elapsedActiveTicks(1000L, 1500L)).isEqualTo(10D);
  }

  @Test
  void fatigueChanceComposesAcrossSkippedTicks() {
    assertThat(SeabornePressureDiver.accumulatedChance(0.2D, 10D))
        .isCloseTo(1D - Math.pow(0.8D, 10D), offset(0.0000001D));
  }
}
