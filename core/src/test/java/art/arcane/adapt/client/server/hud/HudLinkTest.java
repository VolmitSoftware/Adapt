package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.client.link.ClientFrameHandler;
import art.arcane.adapt.client.link.ClientLink;
import art.arcane.adapt.client.link.ClientSessions;
import art.arcane.adapt.client.protocol.ClientCapability;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ServerInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HudLinkTest {
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Test
  void sessionLinkFollowsNegotiationAndWithdrawal() throws ClientProtocolException {
    List<byte[]> sent = new ArrayList<>();
    ClientLink transport = new ClientLink() {
      @Override
      public boolean open(UUID playerId) {
        return true;
      }

      @Override
      public void send(UUID playerId, byte[] frame) {
        sent.add(frame);
      }
    };
    ClientFrameHandler handler = new ClientFrameHandler() {
      @Override
      public void negotiated(UUID playerId, ClientCapability capability) {
      }

      @Override
      public void withdrawn(UUID playerId) {
      }

      @Override
      public void handle(UUID playerId, ClientFrame frame) {
      }
    };
    ClientSessions sessions = new ClientSessions(new ServerInfo("fabric", "26.3", ClientProtocol.ALL), handler,
        () -> transport, () -> 0L);
    HudLink link = HudLink.of(sessions);

    assertThat(link.capable(PLAYER_ID, ClientProtocol.HUD)).isFalse();
    assertThat(link.send(PLAYER_ID, new ServerFrame.HudClear("adapt:xp"))).isFalse();

    sessions.receive(PLAYER_ID, FrameCodec.encodeClient(new ClientFrame.Hello(ClientProtocol.VERSION,
        ClientProtocol.HUD)));
    assertThat(link.capable(PLAYER_ID, ClientProtocol.HUD)).isTrue();
    assertThat(link.capable(PLAYER_ID, ClientProtocol.TOASTS)).isFalse();
    assertThat(link.send(PLAYER_ID, new ServerFrame.HudClear("adapt:xp"))).isTrue();
    assertThat(FrameCodec.decodeServer(sent.get(sent.size() - 1))).isEqualTo(new ServerFrame.HudClear("adapt:xp"));

    sessions.receive(PLAYER_ID, FrameCodec.encodeClient(new ClientFrame.Hello(0, 0)));
    assertThat(link.capable(PLAYER_ID, ClientProtocol.HUD)).isFalse();
  }
}
