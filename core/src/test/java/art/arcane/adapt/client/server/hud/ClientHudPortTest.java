package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.engine.notification.HudPriority;
import art.arcane.adapt.engine.notification.HudSegment;
import art.arcane.adapt.engine.notification.HudSlot;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientHudPortTest {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final HudSegment SEGMENT = new HudSegment("adapt:notice", HudPriority.NOTICE, 2500L,
      List.of(HudSlot.CENTER, HudSlot.RIGHT), "§aHello");

  private final RecordingHudLink link = new RecordingHudLink();
  private final RecordingHudPort delegate = new RecordingHudPort();
  private final ClientHudPort port = new ClientHudPort(delegate);
  private PlayerRef player;

  @BeforeEach
  void install() {
    ClientHud.install(link);
    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(PLAYER_ID);
  }

  @AfterEach
  void uninstall() {
    ClientHud.uninstall();
  }

  @Test
  void capablePlayerGetsAFrameAndTheDelegateNothing() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    port.publish(player, SEGMENT);
    port.clear(player, "adapt:notice");

    assertThat(link.frames()).containsExactly(
        new ServerFrame.HudPublish("adapt:notice", HudPriority.NOTICE, 2500L, List.of(HudSlot.CENTER, HudSlot.RIGHT),
            "§aHello"),
        new ServerFrame.HudClear("adapt:notice"));
    assertThat(delegate.calls()).isEmpty();
  }

  @Test
  void playerWithoutTheClientGoesToTheDelegateUnchanged() {
    port.publish(player, SEGMENT);
    port.clear(player, "adapt:notice");

    assertThat(link.frames()).isEmpty();
    assertThat(delegate.published()).containsExactly(SEGMENT);
    assertThat(delegate.calls()).containsExactly("publish adapt:notice", "clear adapt:notice");
  }

  @Test
  void clientWithoutTheHudBitGoesToTheDelegate() {
    link.grant(PLAYER_ID, ClientProtocol.SCREENS | ClientProtocol.TOASTS);

    port.publish(player, SEGMENT);

    assertThat(link.frames()).isEmpty();
    assertThat(delegate.published()).containsExactly(SEGMENT);
  }

  @Test
  void withdrawnPlayerFallsBackFromTheNextPublish() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);
    port.publish(player, SEGMENT);

    link.withdraw(PLAYER_ID);
    port.publish(player, SEGMENT);

    assertThat(link.frames()).hasSize(1);
    assertThat(delegate.published()).containsExactly(SEGMENT);
  }

  @Test
  void closedChannelFallsBackToTheDelegate() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);
    link.close();

    port.publish(player, SEGMENT);
    port.clear(player, "adapt:notice");

    assertThat(delegate.calls()).containsExactly("publish adapt:notice", "clear adapt:notice");
  }

  @Test
  void clearAllAndShutdownAlwaysReachTheDelegate() {
    link.grant(PLAYER_ID, ClientProtocol.HUD);

    port.clearAll(player);
    port.shutdown();

    assertThat(delegate.calls()).containsExactly("clearAll", "shutdown");
    assertThat(link.frames()).isEmpty();
  }
}
