package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingLastBreathTest {
  @Test
  void higherLevelsShortenThePerPetCooldown() {
    assertThat(TamingLastBreath.cooldownMillis(0.0, 300000, 180000, 60000)).isEqualTo(300000L);
    assertThat(TamingLastBreath.cooldownMillis(1.0, 300000, 180000, 60000)).isEqualTo(120000L);
  }

  @Test
  void cooldownNeverFallsBelowTheConfiguredFloor() {
    assertThat(TamingLastBreath.cooldownMillis(1.0, 300000, 300000, 60000)).isEqualTo(60000L);
  }

  @Test
  void recallArrivalRequiresConfirmedTeleportSuccess() {
    assertThat(TamingLastBreath.successfulRecallTeleport(true, null)).isTrue();
    assertThat(TamingLastBreath.successfulRecallTeleport(false, null)).isFalse();
    assertThat(TamingLastBreath.successfulRecallTeleport(null, null)).isFalse();
    assertThat(TamingLastBreath.successfulRecallTeleport(true, new IllegalStateException())).isFalse();
  }
}
