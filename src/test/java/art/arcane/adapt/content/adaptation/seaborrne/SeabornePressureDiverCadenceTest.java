package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeabornePressureDiverCadenceTest {
  @Test
  void refreshCadenceStaysInsideEffectDurationBounds() {
    assertThat(SeabornePressureDiver.refreshIntervalMillis(4)).isEqualTo(250L);
    assertThat(SeabornePressureDiver.refreshIntervalMillis(60)).isEqualTo(750L);
    assertThat(SeabornePressureDiver.refreshIntervalMillis(100)).isEqualTo(750L);
  }
}
