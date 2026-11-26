package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingTeleportOutcomeTest {
  @Test
  void beastRecallCommitsOnlyConfirmedTeleportSuccess() {
    assertThat(TameRecall.successfulRecallTeleport(true, null)).isTrue();
    assertThat(TameRecall.successfulRecallTeleport(false, null)).isFalse();
    assertThat(TameRecall.successfulRecallTeleport(null, null)).isFalse();
    assertThat(TameRecall.successfulRecallTeleport(true, new IllegalStateException())).isFalse();
  }
}
