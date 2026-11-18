package art.arcane.adapt.content.adaptation.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryFieldNotesTest {
  @Test
  void bankingNeverExceedsTheSpeciesCap() {
    assertThat(DiscoveryFieldNotes.bankTarget(0.9D, 0.15D, 1.0D)).isEqualTo(1.0D);
    assertThat(DiscoveryFieldNotes.bankTarget(0.0D, 0.15D, 3.0D)).isEqualTo(0.15D);
    assertThat(DiscoveryFieldNotes.bankTarget(3.0D, 0.15D, 3.0D)).isEqualTo(3.0D);
  }

  @Test
  void capAndBountyScaleUpWithLevelAndStayNonNegative() {
    assertThat(DiscoveryFieldNotes.perSpeciesCap(1.0D, 0.5D, 2.5D)).isEqualTo(3.0D);
    assertThat(DiscoveryFieldNotes.perSpeciesCap(0.0D, 0.5D, 2.5D)).isEqualTo(0.5D);
    assertThat(DiscoveryFieldNotes.perSpeciesCap(1.0D, 0.5D, 2.5D))
        .isGreaterThan(DiscoveryFieldNotes.perSpeciesCap(0.2D, 0.5D, 2.5D));
    assertThat(DiscoveryFieldNotes.firstKillXp(0.2D, 120D, 240D)).isEqualTo(168D);
  }
}
