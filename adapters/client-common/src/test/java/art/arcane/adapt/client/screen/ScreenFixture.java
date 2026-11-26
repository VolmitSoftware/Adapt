package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.state.ClientActions;
import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.ui.ScreenHarness;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.adapt.client.ui.kit.ManualClock;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.theme.PageThemes;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.PlayerView;

import java.util.ArrayList;
import java.util.List;

/** The Adapt pages over the preview samples, a fake host and a recording sender, outside Minecraft. */
final class ScreenFixture {
    final ManualClock clock = new ManualClock();
    final ClientState state = new ClientState();
    final List<ClientFrame> sent = new ArrayList<>();
    boolean connected = true;
    final ClientActions actions = new ClientActions(state, (ClientFrame frame) -> connected && sent.add(frame));
    final FakeHost host;
    KitDraw draw;
    final List<String> hudFeedback = new ArrayList<>();
    final ScreenRouter router;

    ScreenFixture() {
        this(427, 240, 2.0);
    }

    ScreenFixture(int width, int height, double scale) {
        this(width, height, scale, PreviewSamples.catalog(), PreviewSamples.player());
    }

    ScreenFixture(int width, int height, double scale, CatalogView catalog, PlayerView player) {
        this(width, height, scale, catalog, player, false);
    }

    /** {@code vanilla} measures text as the vanilla font does, for tests about what fits a page. */
    ScreenFixture(int width, int height, double scale, CatalogView catalog, PlayerView player, boolean vanilla) {
        host = new FakeHost(width, height);
        draw = vanilla ? KitDraw.vanilla(width, height, scale) : new KitDraw(width, height, scale);
        state.negotiated(ClientProtocol.VERSION, new ServerInfo("test", "26.3", ClientProtocol.SCREENS | ClientProtocol.PANEL));
        state.acceptCatalog(catalog);
        state.acceptPlayer(player);
        router = new ScreenRouter(host, state, actions, UiTheme.DEFAULT, clock.motion(), PageThemes.defaults(),
                (FeedbackKind kind, String text) -> hudFeedback.add(kind + " " + text));
    }

    @SuppressWarnings("unchecked")
    <T extends AdaptScreen> T open(Route route) {
        router.open(route);
        clock.advance(KitScreen.OPEN_MILLIS);
        render();
        return (T) router.current();
    }

    AdaptScreen current() {
        return router.current();
    }

    /** Resizes the window or changes the GUI scale under the open page, as Minecraft does: the page is told and laid out again. */
    void resize(int width, int height, double scale) {
        draw = draw.measuresVanilla() ? KitDraw.vanilla(width, height, scale) : new KitDraw(width, height, scale);
        ScreenHarness.attach(router.current(), host, host, width, height);
    }

    void render() {
        AdaptScreen screen = router.current();
        if (screen != null) {
            screen.render(draw, 0, 0, 0.0F);
        }
    }

    /** Turns to the leaf holding {@code node} like a player would, then clicks its centre. */
    void click(UiNode node) {
        AdaptScreen screen = router.current();
        render();
        screen.reveal(node);
        render();
        UiMouseEvent event = new UiMouseEvent(node.bounds().centerX(), node.bounds().centerY(), UiMouseButton.LEFT, UiModifiers.NONE);
        screen.mouseClicked(event);
        screen.mouseReleased(event);
        render();
    }

    boolean key(UiKey key) {
        boolean used = router.current().keyPressed(new UiKeyEvent(key, UiModifiers.NONE));
        render();
        return used;
    }

    /** Whether the node {@code id} is on the page and showing. */
    boolean shows(String id) {
        UiNode node = find(id);
        return node != null && node.shown();
    }

    UiNode find(String id) {
        AdaptScreen screen = router.current();
        UiNode node = screen.root().find(id);
        if (node == null) {
            for (UiNode overlay : screen.overlays()) {
                node = overlay.find(id);
                if (node != null) {
                    break;
                }
            }
        }
        return node;
    }
}
