package art.arcane.adapt.client.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientNegotiationTest {
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void versionZeroOrLessWithdraws() {
    assertThat(ClientNegotiation.accept(0, ClientProtocol.ALL, ClientProtocol.ALL)).isEmpty();
    assertThat(ClientNegotiation.accept(-1, ClientProtocol.ALL, ClientProtocol.ALL)).isEmpty();
  }

  @Test
  void aNewerClientSpeaksTheServerVersionWithSharedBits() {
    ClientCapability capability = ClientNegotiation.accept(ClientProtocol.VERSION + 3,
        ClientProtocol.SCREENS | ClientProtocol.HUD | 1 << 20, ClientProtocol.SCREENS | ClientProtocol.TOASTS).orElseThrow();

    assertThat(capability.version()).isEqualTo(ClientProtocol.VERSION);
    assertThat(capability.mask()).isEqualTo(ClientProtocol.SCREENS);
    assertThat(capability.has(ClientProtocol.SCREENS)).isTrue();
    assertThat(capability.has(ClientProtocol.HUD)).isFalse();
  }

  @Test
  void capabilitiesAreStoredPerPlayerAndForgottenOnWithdrawal() {
    ClientCapabilities capabilities = new ClientCapabilities();

    capabilities.accept(PLAYER, 1, ClientProtocol.PANEL | ClientProtocol.ITEMS, ClientProtocol.ALL);

    assertThat(capabilities.has(PLAYER, ClientProtocol.PANEL)).isTrue();
    assertThat(capabilities.has(PLAYER, ClientProtocol.ITEMS)).isTrue();
    assertThat(capabilities.has(PLAYER, ClientProtocol.SCREENS)).isFalse();
    assertThat(capabilities.has(UUID.randomUUID(), ClientProtocol.PANEL)).isFalse();
    assertThat(capabilities.has(null, ClientProtocol.PANEL)).isFalse();

    assertThat(capabilities.accept(PLAYER, 0, 0, ClientProtocol.ALL)).isEmpty();
    assertThat(capabilities.capability(PLAYER)).isEmpty();
  }

  @Test
  void clearReportsWhetherThePlayerHadACapability() {
    ClientCapabilities capabilities = new ClientCapabilities();
    capabilities.accept(PLAYER, 1, ClientProtocol.ALL, ClientProtocol.ALL);

    assertThat(capabilities.clear(PLAYER)).isTrue();
    assertThat(capabilities.clear(PLAYER)).isFalse();
  }
}
