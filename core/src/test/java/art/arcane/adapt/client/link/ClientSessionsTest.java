package art.arcane.adapt.client.link;

import art.arcane.adapt.client.protocol.ClientCapability;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameFragments;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ServerInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientSessionsTest {
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final ServerInfo SERVER = new ServerInfo("neoforge", "26.3",
      ClientProtocol.SCREENS | ClientProtocol.PANEL | ClientProtocol.HUD);

  private final RecordingLink link = new RecordingLink();
  private final RecordingHandler handler = new RecordingHandler();
  private long now = 1_000L;
  private final ClientSessions sessions = new ClientSessions(SERVER, handler, () -> link, () -> now);

  @Test
  void helloNegotiatesAndAcceptsWithTheSharedMask() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));

    int shared = ClientProtocol.SCREENS | ClientProtocol.PANEL | ClientProtocol.HUD;
    assertThat(handler.negotiated).containsExactly(new ClientCapability(1, shared));
    assertThat(link.decoded()).containsExactly(new ServerFrame.Accept(1, new ServerInfo("neoforge", "26.3", shared)));
    assertThat(sessions.has(PLAYER, ClientProtocol.HUD)).isTrue();
    assertThat(sessions.has(PLAYER, ClientProtocol.TOASTS)).isFalse();
  }

  @Test
  void framesBeforeHelloAreIgnoredAndFramesAfterArePassedOn() throws ClientProtocolException {
    ClientFrame open = new ClientFrame.Open(new Route.Skills());

    sessions.receive(PLAYER, FrameCodec.encodeClient(open));
    assertThat(handler.frames).isEmpty();

    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    sessions.receive(PLAYER, FrameCodec.encodeClient(open));
    assertThat(handler.frames).containsExactly(open);
  }

  @Test
  void helloZeroWithdrawsAndStopsDispatch() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(0, 0)));
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.PanelClosed(1)));

    assertThat(handler.withdrawn).containsExactly(PLAYER);
    assertThat(sessions.capability(PLAYER)).isEmpty();
    assertThat(handler.frames).isEmpty();
    assertThat(sessions.send(PLAYER, new ServerFrame.HudClear("x"))).isFalse();
  }

  @Test
  void withdrawingWithoutNegotiatingReportsNothing() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(0, 0)));
    sessions.quit(PLAYER);

    assertThat(handler.withdrawn).isEmpty();
  }

  @Test
  void quitForgetsTheCapability() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    sessions.quit(PLAYER);

    assertThat(sessions.has(PLAYER, ClientProtocol.SCREENS)).isFalse();
    assertThat(handler.withdrawn).containsExactly(PLAYER);
  }

  @Test
  void repeatedMalformedFramesResetThePlayerToTheFallback() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));

    sessions.receive(PLAYER, new byte[] {(byte) 99});
    sessions.receive(PLAYER, new byte[] {(byte) 99});
    assertThat(sessions.capability(PLAYER)).isPresent();

    sessions.receive(PLAYER, new byte[] {(byte) 99});
    assertThat(sessions.capability(PLAYER)).isEmpty();
    assertThat(handler.withdrawn).containsExactly(PLAYER);
  }

  @Test
  void floodsBeyondTheRateAreDropped() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    byte[] closed = FrameCodec.encodeClient(new ClientFrame.PanelClosed(1));
    for (int index = 0; index < 100; index++) {
      sessions.receive(PLAYER, closed);
    }

    assertThat(handler.frames).hasSizeLessThan(60);
  }

  @Test
  void sendEncodesOnlyForNegotiatedPlayersWithAnOpenChannel() throws ClientProtocolException {
    ServerFrame toast = new ServerFrame.HudClear("adapt:xp");
    assertThat(sessions.send(PLAYER, toast)).isFalse();

    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    assertThat(sessions.send(PLAYER, toast)).isTrue();

    link.open.remove(PLAYER);
    assertThat(sessions.send(PLAYER, toast)).isFalse();
    assertThat(link.decoded()).endsWith(toast);
  }

  @Test
  void aFrameOverTheLinkLimitIsSentAsBackToBackFragmentsThatReassemble() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    link.maxPayload = 64;
    ServerFrame feedback = new ServerFrame.Feedback(FeedbackKind.INFO, "x".repeat(300));
    int before = link.sent.size();

    assertThat(sessions.send(PLAYER, feedback)).isTrue();

    List<byte[]> payloads = link.sent.subList(before, link.sent.size());
    assertThat(payloads).hasSizeGreaterThan(1).allSatisfy((byte[] payload) -> {
      assertThat(payload.length).isLessThanOrEqualTo(64);
      assertThat(payload[0]).isEqualTo((byte) FrameFragments.OPCODE);
    });
    FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);
    byte[] whole = null;
    for (byte[] payload : payloads) {
      whole = assembler.accept(payload);
    }
    assertThat(FrameCodec.decodeServer(whole)).isEqualTo(feedback);
  }

  @Test
  void aFrameThatFitsIsSentUnchanged() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    ServerFrame clear = new ServerFrame.HudClear("adapt:xp");

    sessions.send(PLAYER, clear);

    assertThat(link.sent.get(link.sent.size() - 1)).isEqualTo(FrameCodec.encodeServer(clear));
  }

  @Test
  void fragmentedClientFramesAreJoinedBeforeDispatch() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    ClientFrame input = new ClientFrame.PanelInput(3, 4, 5, "y".repeat(500));

    for (byte[] payload : FrameFragments.split(FrameCodec.encodeClient(input), 100)) {
      sessions.receive(PLAYER, payload);
    }

    assertThat(handler.frames).containsExactly(input);
  }

  @Test
  void aFrameBetweenFragmentsIsAViolationAndTheNextFrameStillWorks() throws ClientProtocolException {
    sessions.receive(PLAYER, FrameCodec.encodeClient(new ClientFrame.Hello(1, ClientProtocol.ALL)));
    List<byte[]> pieces = FrameFragments.split(FrameCodec.encodeClient(new ClientFrame.PanelInput(3, 4, 5, "z".repeat(500))), 100);
    ClientFrame closed = new ClientFrame.PanelClosed(9);

    sessions.receive(PLAYER, pieces.get(0));
    sessions.receive(PLAYER, FrameCodec.encodeClient(closed));
    sessions.receive(PLAYER, FrameCodec.encodeClient(closed));

    assertThat(handler.frames).containsExactly(closed);
    assertThat(sessions.capability(PLAYER)).isPresent();
  }

  @Test
  void theNoneLinkIsClosed() {
    assertThat(ClientLink.NONE.open(PLAYER)).isFalse();
    assertThat(ClientLink.current()).isSameAs(ClientLink.NONE);
  }

  private static final class RecordingLink implements ClientLink {
    private final Set<UUID> open = new HashSet<>(Set.of(PLAYER));
    private final List<byte[]> sent = new ArrayList<>();
    private int maxPayload = FrameLimits.MAX_S2C_BYTES;

    @Override
    public int maxPayloadBytes() {
      return maxPayload;
    }

    @Override
    public boolean open(UUID playerId) {
      return open.contains(playerId);
    }

    @Override
    public void send(UUID playerId, byte[] frame) {
      sent.add(frame);
    }

    List<ServerFrame> decoded() throws ClientProtocolException {
      List<ServerFrame> frames = new ArrayList<>();
      for (byte[] frame : sent) {
        frames.add(FrameCodec.decodeServer(frame));
      }
      return frames;
    }
  }

  private static final class RecordingHandler implements ClientFrameHandler {
    private final List<ClientCapability> negotiated = new ArrayList<>();
    private final List<UUID> withdrawn = new ArrayList<>();
    private final List<ClientFrame> frames = new ArrayList<>();

    @Override
    public void negotiated(UUID playerId, ClientCapability capability) {
      negotiated.add(capability);
    }

    @Override
    public void withdrawn(UUID playerId) {
      withdrawn.add(playerId);
    }

    @Override
    public void handle(UUID playerId, ClientFrame frame) {
      frames.add(frame);
    }
  }
}
