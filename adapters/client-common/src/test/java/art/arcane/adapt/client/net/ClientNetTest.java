package art.arcane.adapt.client.net;

import art.arcane.adapt.api.notification.HudSlot;
import art.arcane.adapt.client.config.ClientConfig;
import art.arcane.adapt.client.hud.HudClock;
import art.arcane.adapt.client.hud.HudModel;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameFragments;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.platform.menu.ClickKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientNetTest {
    private static final ServerInfo SERVER = new ServerInfo("fabric", "26.3", ClientProtocol.PANEL);
    private static final CatalogView CATALOG = new CatalogView(42L, "en_us", List.of());
    private static final PlayerHeader HEADER = new PlayerHeader(3, 0.25D, 1, 10, 0L, true);
    private static final PlayerView PLAYER = new PlayerView(HEADER, List.of(new SkillLineView("rift", 10D, 1, 0.1D, 90D, 0L)),
            Map.of("rift-blink", 1), List.of(), PlayerAccess.NONE);

    private static final class FakeTransport implements ClientTransport {
        private final List<ClientFrame> sent = new ArrayList<>();
        private boolean ready;

        @Override
        public boolean ready() {
            return ready;
        }

        @Override
        public void send(byte[] frame) {
            try {
                sent.add(FrameCodec.decodeClient(frame));
            } catch (ClientProtocolException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static final class RecordingPanels implements PanelSink {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void open(PanelView panel) {
            calls.add("open " + panel.sessionId());
        }

        @Override
        public void update(PanelView panel) {
            calls.add("update " + panel.sessionId());
        }

        @Override
        public void close(int sessionId) {
            calls.add("close " + sessionId);
        }

        @Override
        public void reset() {
            calls.add("reset");
        }
    }

    private final FakeTransport transport = new FakeTransport();
    private final ClientNet net = new ClientNet(transport, ClientConfig.memory());

    private static byte[] encode(ServerFrame frame) {
        try {
            return FrameCodec.encodeServer(frame);
        } catch (ClientProtocolException exception) {
            throw new AssertionError(exception);
        }
    }

    private void negotiate() {
        net.connected();
        transport.ready = true;
        net.tick();
        net.receive(encode(new ServerFrame.Accept(1, SERVER)));
    }

    @Test
    void jumpKeyIsOfferedAndSentOnlyWhenTheServerAcceptedIt() {
        net.offer(ClientProtocol.JUMPS);
        assertFalse(net.jumpKey(true));
        negotiate();
        assertEquals(new ClientFrame.Hello(ClientProtocol.VERSION, ClientProtocol.JUMPS), transport.sent.get(0));
        assertFalse(net.jumpKey(true));

        net.receive(encode(new ServerFrame.Accept(1, new ServerInfo("forge", "1.20.1", ClientProtocol.JUMPS))));
        assertTrue(net.jumpKey(true));
        assertEquals(new ClientFrame.JumpKey(true), transport.sent.get(transport.sent.size() - 1));
    }

    @Test
    void helloGoesOutOnceTheChannelIsReadyAndOnlyOncePerConnection() {
        net.connected();
        net.tick();
        assertTrue(transport.sent.isEmpty());

        transport.ready = true;
        net.tick();
        net.tick();
        assertEquals(List.of(new ClientFrame.Hello(ClientProtocol.VERSION, 0)), transport.sent);

        net.disconnected();
        net.connected();
        net.tick();
        assertEquals(2, transport.sent.size());
    }

    @Test
    void noHelloBeforeAConnection() {
        transport.ready = true;
        net.tick();
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void acceptCatalogPlayerAndDeltasBuildTheState() {
        List<Object> events = new ArrayList<>();
        net.state().onConnected(events::add);
        net.state().onCatalog(events::add);
        net.state().onPlayer(events::add);
        negotiate();
        net.receive(encode(new ServerFrame.Catalog(CATALOG)));
        net.receive(encode(new ServerFrame.Player(PLAYER)));
        PlayerHeader header = new PlayerHeader(4, 0D, 2, 10, 1L, true);
        net.receive(encode(new ServerFrame.PlayerUpdate(new PlayerDelta(header, List.of(), Map.of("rift-blink", 0, "rift-gate", 2),
                List.of(), List.of(), null))));

        assertTrue(net.state().negotiated());
        assertEquals(1, net.state().version());
        assertTrue(net.state().has(ClientProtocol.PANEL));
        assertFalse(net.state().has(ClientProtocol.HUD));
        assertEquals(CATALOG, net.state().catalog());
        assertEquals(header, net.state().player().header());
        assertEquals(Map.of("rift-gate", 2), net.state().player().adaptationLevels());
        assertEquals(4, events.size());
        assertEquals(SERVER, events.get(0));
    }

    @Test
    void framesBeforeAcceptOrWithoutHelloAreIgnored() {
        net.connected();
        net.receive(encode(new ServerFrame.Accept(1, SERVER)));
        assertFalse(net.state().negotiated());

        transport.ready = true;
        net.tick();
        net.receive(encode(new ServerFrame.Catalog(CATALOG)));
        assertNull(net.state().catalog());
    }

    @Test
    void aDeltaBeforeTheFirstPlayerViewIsDropped() {
        negotiate();
        net.receive(encode(new ServerFrame.PlayerUpdate(new PlayerDelta(HEADER, List.of(), Map.of(), List.of(), List.of(), null))));
        assertNull(net.state().player());
    }

    @Test
    void turningTheNativeUiOffWithdrawsAndTurningItOnNegotiatesAgain() {
        List<String> ended = new ArrayList<>();
        net.state().onDisconnected(() -> ended.add("ended"));
        negotiate();

        net.nativeUi(false);
        net.tick();

        assertEquals(new ClientFrame.Hello(0, 0), transport.sent.get(1));
        assertEquals(2, transport.sent.size());
        assertFalse(net.state().negotiated());
        assertEquals(List.of("ended"), ended);
        assertFalse(net.config().ui().nativeUi());

        net.nativeUi(true);
        net.tick();
        assertEquals(new ClientFrame.Hello(ClientProtocol.VERSION, 0), transport.sent.get(2));
    }

    @Test
    void disabledNativeUiNeverSaysHello() {
        net.nativeUi(false);
        net.connected();
        transport.ready = true;
        net.tick();
        assertTrue(transport.sent.isEmpty());
    }

    private static List<byte[]> fragments(ServerFrame frame, int maxPayload) {
        try {
            return FrameFragments.split(encode(frame), maxPayload);
        } catch (ClientProtocolException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void aFragmentedFrameIsAppliedOnceItsLastFragmentArrives() {
        negotiate();
        List<byte[]> pieces = fragments(new ServerFrame.Catalog(CATALOG), 8);

        for (int index = 0; index < pieces.size() - 1; index++) {
            net.receive(pieces.get(index));
            assertNull(net.state().catalog());
        }
        net.receive(pieces.get(pieces.size() - 1));

        assertEquals(CATALOG, net.state().catalog());
    }

    @Test
    void aFrameBetweenFragmentsDropsTheSession() {
        negotiate();
        List<byte[]> pieces = fragments(new ServerFrame.Catalog(CATALOG), 8);

        net.receive(pieces.get(0));
        net.receive(encode(new ServerFrame.Player(PLAYER)));

        assertFalse(net.state().negotiated());
    }

    @Test
    void aDisconnectDiscardsAPartialFrame() {
        negotiate();
        List<byte[]> pieces = fragments(new ServerFrame.Catalog(CATALOG), 8);
        net.receive(pieces.get(0));

        net.disconnected();
        negotiate();
        net.receive(encode(new ServerFrame.Catalog(CATALOG)));

        assertTrue(net.state().negotiated());
        assertEquals(CATALOG, net.state().catalog());
    }

    @Test
    void disconnectResetsStateAndSinks() {
        RecordingPanels panels = new RecordingPanels();
        net.install(ClientSinks.NONE.withPanels(panels));
        negotiate();
        net.receive(encode(new ServerFrame.Catalog(CATALOG)));

        net.disconnected();

        assertFalse(net.state().negotiated());
        assertNull(net.state().catalog());
        assertTrue(panels.calls.contains("reset"));
        net.receive(encode(new ServerFrame.Catalog(CATALOG)));
        assertNull(net.state().catalog());
    }

    @Test
    void aMalformedFrameDropsTheSessionUntilTheNextConnection() {
        negotiate();
        net.receive(encode(new ServerFrame.Catalog(CATALOG)));

        net.receive(new byte[]{(byte) 0x7F, 1, 2});

        assertFalse(net.state().negotiated());
        assertEquals(new ClientFrame.Hello(0, 0), transport.sent.get(transport.sent.size() - 1));
        int sent = transport.sent.size();
        net.tick();
        net.receive(encode(new ServerFrame.Accept(1, SERVER)));
        assertEquals(sent, transport.sent.size());
        assertFalse(net.state().negotiated());

        net.disconnected();
        net.connected();
        net.tick();
        assertEquals(new ClientFrame.Hello(ClientProtocol.VERSION, 0), transport.sent.get(transport.sent.size() - 1));
    }

    @Test
    void anAcceptWithAnUnknownVersionDropsTheSession() {
        net.connected();
        transport.ready = true;
        net.tick();
        net.receive(encode(new ServerFrame.Accept(ClientProtocol.VERSION + 1, SERVER)));
        assertFalse(net.state().negotiated());
        assertEquals(new ClientFrame.Hello(0, 0), transport.sent.get(1));
    }

    @Test
    void framesReachTheirSinksAndSinksSetTheCapabilityMask() {
        RecordingPanels panels = new RecordingPanels();
        List<Route> routes = new ArrayList<>();
        List<String> feedback = new ArrayList<>();
        net.install(ClientSinks.NONE.withPanels(panels).withRoutes(routes::add)
                .withFeedback((FeedbackKind kind, String text) -> feedback.add(kind + " " + text)));
        negotiate();
        PanelView panel = new PanelView(7, 1, "Skills", 3, PanelLayout.GRID, null, List.of(), List.of());
        net.receive(encode(new ServerFrame.PanelOpen(panel)));
        net.receive(encode(new ServerFrame.PanelUpdate(panel)));
        net.receive(encode(new ServerFrame.PanelClose(7)));
        net.receive(encode(new ServerFrame.OpenRoute(new Route.Skill("rift"))));
        net.receive(encode(new ServerFrame.Feedback(FeedbackKind.FAILURE, "Not enough knowledge")));

        assertEquals(new ClientFrame.Hello(ClientProtocol.VERSION, ClientProtocol.PANEL | ClientProtocol.SCREENS), transport.sent.get(0));
        assertEquals(List.of("reset", "open 7", "update 7", "close 7"), panels.calls);
        assertEquals(List.of(new Route.Skill("rift")), routes);
        assertEquals(List.of("FAILURE Not enough knowledge"), feedback);
    }

    @Test
    void hudClearAllReachesTheHudSink() {
        HudModel hud = new HudModel(HudClock.SYSTEM);
        net.install(ClientSinks.NONE.withHud(hud));
        negotiate();
        net.receive(encode(new ServerFrame.HudPublish("adapt:notice", 10, 5000L, List.of(HudSlot.CENTER), "hi")));
        net.receive(encode(new ServerFrame.HudStatus("adapt:status:a", HudGlyph.CHARGES, "", IconRef.item("minecraft:stone"), "A",
                System.currentTimeMillis(), 0L, 2, 5, false)));

        net.receive(encode(new ServerFrame.HudClearAll()));

        assertTrue(hud.notices().isEmpty());
        assertTrue(hud.statuses().isEmpty());
    }

    @Test
    void installingNewSinksRenegotiates() {
        negotiate();
        net.install(ClientSinks.NONE.withPanels(new RecordingPanels()));
        net.tick();
        assertEquals(new ClientFrame.Hello(ClientProtocol.VERSION, ClientProtocol.PANEL), transport.sent.get(1));
    }

    @Test
    void actionsSendOnlyInsideANegotiatedSession() {
        assertFalse(net.actions().panelClosed(3));
        negotiate();
        assertFalse(net.actions().learn("rift-blink", 2));
        net.receive(encode(new ServerFrame.Catalog(CATALOG)));

        assertTrue(net.actions().learn("rift-blink", 2));
        assertTrue(net.actions().panelClick(3, 4, 5, ClickKind.SHIFT_LEFT));
        assertTrue(net.actions().open(new Route.Skills()));

        assertEquals(List.of(new ClientFrame.Learn("rift-blink", 2, 42L), new ClientFrame.PanelClick(3, 4, 5, ClickKind.SHIFT_LEFT),
                new ClientFrame.Open(new Route.Skills())), transport.sent.subList(1, 4));
    }
}
