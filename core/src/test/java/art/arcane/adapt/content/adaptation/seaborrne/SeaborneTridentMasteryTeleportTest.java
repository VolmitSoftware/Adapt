package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeaborneTridentMasteryTeleportTest {
  @Test
  void releaseContinuesOnlyAfterConfirmedTeleportSuccess() {
    assertThat(SeaborneTridents.successfulReleaseTeleport(true, null)).isTrue();
    assertThat(SeaborneTridents.successfulReleaseTeleport(false, null)).isFalse();
    assertThat(SeaborneTridents.successfulReleaseTeleport(null, null)).isFalse();
    assertThat(SeaborneTridents.successfulReleaseTeleport(true, new IllegalStateException())).isFalse();
  }
}
