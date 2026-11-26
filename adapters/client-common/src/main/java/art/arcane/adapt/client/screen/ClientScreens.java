package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.AdaptKey;
import art.arcane.adapt.client.net.ClientNet;
import art.arcane.adapt.client.net.PanelSink;
import art.arcane.adapt.client.panel.PanelActions;
import art.arcane.adapt.client.panel.PanelScreens;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.state.ClientActions;
import art.arcane.adapt.client.theme.PageThemes;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.platform.menu.ClickKind;

/**
 * Turns the native UI on for a running client: designed pages for the server's routes and feedback (on the HUD when no
 * page is open), native panels for
 * every other server menu, and the "Open skills" key. With these sinks installed the client offers SCREENS and PANEL
 * in its {@code Hello}.
 */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static ScreenRouter install(AdaptClient client) {
        ClientNet net = client.net();
        ScreenText.install(client.host());
        ScreenRouter router = new ScreenRouter(client.host(), net.state(), net.actions(), UiTheme.DEFAULT, UiMotion.system(),
                new PageThemes(() -> net.config().ui(), net.config()::save), client.hud()::feedback);
        PanelScreens panels = new PanelScreens(client.host(), panelActions(net.actions()));
        net.install(net.sinks().withPanels(panelSink(panels)).withRoutes(router).withFeedback(router));
        client.bindKey(AdaptKey.OPEN_SKILLS, () -> openSkills(net));
        return router;
    }

    /** Asks the server for the skills page; it answers with the route, so the server stays in charge of what opens. */
    static boolean openSkills(ClientNet net) {
        return net.state().has(ClientProtocol.SCREENS) && net.actions().open(new Route.Skills());
    }

    static PanelSink panelSink(PanelScreens panels) {
        return new PanelSink() {
            @Override
            public void open(PanelView panel) {
                panels.open(panel);
            }

            @Override
            public void update(PanelView panel) {
                panels.update(panel);
            }

            @Override
            public void close(int sessionId) {
                panels.close(sessionId);
            }

            @Override
            public void reset() {
                panels.reset();
            }
        };
    }

    static PanelActions panelActions(ClientActions actions) {
        return new PanelActions() {
            @Override
            public void click(int sessionId, int revision, int slot, ClickKind kind) {
                actions.panelClick(sessionId, revision, slot, kind);
            }

            @Override
            public void input(int sessionId, int revision, int slot, String text) {
                actions.panelInput(sessionId, revision, slot, text);
            }

            @Override
            public void closed(int sessionId) {
                actions.panelClosed(sessionId);
            }
        };
    }
}
