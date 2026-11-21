package art.arcane.adapt.client;

import art.arcane.adapt.client.config.ClientConfig;
import art.arcane.adapt.client.net.ClientNet;
import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.client.panel.NativePanelScreen;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.screen.AdaptScreen;
import art.arcane.adapt.client.screen.ClientScreens;
import art.arcane.adapt.client.screen.PreviewSamples;
import art.arcane.adapt.client.screen.ScreenRouter;
import art.arcane.adapt.client.screen.SkillsScreen;
import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.UiWorld;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientScreensGlueTest {
    private final List<ClientFrame> sent = new ArrayList<>();
    private final List<AdaptKey> pressed = new ArrayList<>();
    private final FakeHost host = new FakeHost(427, 240);
    private final ClientNet net = new ClientNet(new ClientTransport() {
        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public void send(byte[] frame) {
            try {
                sent.add(FrameCodec.decodeClient(frame));
            } catch (ClientProtocolException exception) {
                throw new AssertionError(exception);
            }
        }
    }, ClientConfig.memory());
    private final AdaptClient client = new AdaptClient(host, (Consumer<AdaptKey> consumer) -> {
        pressed.forEach(consumer);
        pressed.clear();
    }, net, UiWorld.NONE, false);
    private final ScreenRouter router = ClientScreens.install(client);

    private void receive(ServerFrame frame) {
        try {
            net.receive(FrameCodec.encodeServer(frame));
        } catch (ClientProtocolException exception) {
            throw new AssertionError(exception);
        }
    }

    private void negotiate() {
        client.connected();
        client.tick();
        receive(new ServerFrame.Accept(ClientProtocol.VERSION, new ServerInfo("paper", "26.3", ClientProtocol.SCREENS | ClientProtocol.PANEL)));
        receive(new ServerFrame.Catalog(PreviewSamples.catalog()));
        receive(new ServerFrame.Player(PreviewSamples.player()));
    }

    @Test
    void installedSinksOfferScreensAndPanelsInHello() {
        int screens = ClientProtocol.SCREENS | ClientProtocol.PANEL;
        assertEquals(screens, net.sinks().capabilities() & screens);
        client.connected();
        client.tick();
        assertEquals(List.of(new ClientFrame.Hello(ClientProtocol.VERSION, net.sinks().capabilities())), sent);
    }

    @Test
    void skillsKeyAsksTheServerOnlyOnceNegotiated() {
        client.connected();
        pressed.add(AdaptKey.OPEN_SKILLS);
        client.tick();
        assertEquals(1, sent.size());
        negotiate();
        sent.clear();
        pressed.add(AdaptKey.OPEN_SKILLS);
        client.tick();
        assertEquals(List.of(new ClientFrame.Open(new Route.Skills())), sent);
        assertNull(host.current());
    }

    @Test
    void serverRoutesOpenPagesAndFeedbackReachesThem() {
        negotiate();
        receive(new ServerFrame.OpenRoute(new Route.Skills()));
        assertInstanceOf(SkillsScreen.class, host.current());
        receive(new ServerFrame.Feedback(FeedbackKind.FAILURE, "No"));
        AdaptScreen page = router.current();
        assertEquals(Snackbar.Tone.FAILURE, page.snackbar().tone());
        assertEquals("No", page.snackbar().text());
    }

    @Test
    void serverMenusOpenAsNativePanelsAndClicksGoBack() {
        negotiate();
        sent.clear();
        receive(new ServerFrame.PanelOpen(new PanelView(5, 1, "Configure", 3, PanelLayout.LIST, null, List.of(),
                List.of(new PanelTile(4, IconRef.item("minecraft:stone"), 1, "Stone", List.of(), false, null, TileRole.ENTRY, "", "", null,
                        List.of())))));
        NativePanelScreen panel = assertInstanceOf(NativePanelScreen.class, host.current());
        assertEquals(5, panel.sessionId());
        panel.close();
        assertEquals(List.of(new ClientFrame.PanelClosed(5)), sent);
    }

    @Test
    void sessionEndClosesOpenPages() {
        negotiate();
        receive(new ServerFrame.OpenRoute(new Route.Skill("agility")));
        assertTrue(host.current() instanceof AdaptScreen);
        client.disconnected();
        assertNull(host.current());
    }
}
