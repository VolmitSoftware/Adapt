package art.arcane.adapt.content.adaptation.stealth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StealthShadowDecoyPacketsTest {
  @Test
  void tabRemovalDelayPreservesNeverRemoveAndConvertsTicks() {
    assertThat(PacketPlayerDecoy.tabRemovalDeadline(1_000L, -1)).isEqualTo(-1L);
    assertThat(PacketPlayerDecoy.tabRemovalDeadline(1_000L, 0)).isEqualTo(1_000L);
    assertThat(PacketPlayerDecoy.tabRemovalDeadline(1_000L, 40)).isEqualTo(3_000L);
  }
}
