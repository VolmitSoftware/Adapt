package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingTeleportOutcomeTest {
  @Test
  void beastRecallCommitsOnlyConfirmedTeleportSuccess() {
    assertThat(TamingBeastRecall.successfulRecallTeleport(true, null)).isTrue();
    assertThat(TamingBeastRecall.successfulRecallTeleport(false, null)).isFalse();
    assertThat(TamingBeastRecall.successfulRecallTeleport(null, null)).isFalse();
    assertThat(TamingBeastRecall.successfulRecallTeleport(true, new IllegalStateException())).isFalse();
  }
}
