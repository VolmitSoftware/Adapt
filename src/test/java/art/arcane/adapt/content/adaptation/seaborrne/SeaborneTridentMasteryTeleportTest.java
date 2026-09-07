package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeaborneTridentMasteryTeleportTest {
  @Test
  void releaseContinuesOnlyAfterConfirmedTeleportSuccess() {
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(true, null)).isTrue();
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(false, null)).isFalse();
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(null, null)).isFalse();
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(true, new IllegalStateException())).isFalse();
  }
}
