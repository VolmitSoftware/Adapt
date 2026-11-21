package art.arcane.adapt.client;

import art.arcane.adapt.client.hud.HudClock;
import art.arcane.adapt.client.hud.HudController;
import art.arcane.adapt.client.hud.HudLayoutStore;
import art.arcane.adapt.client.hud.HudSamples;
import art.arcane.adapt.client.icons.IconSheet;
import art.arcane.adapt.client.insight.InsightHud;
import art.arcane.adapt.client.items.ItemIndex;
import art.arcane.adapt.client.net.ClientNet;
import art.arcane.adapt.client.panel.PanelPreview;
import art.arcane.adapt.client.screen.ClientScreens;
import art.arcane.adapt.client.screen.ScreenPreview;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiWorld;
import art.arcane.adapt.mod.AdaptModIdentity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The client side of Adapt, shared by every loader and version. Loaders forward ticks, connection changes and HUD
 * rendering here; nothing in this class names a Minecraft type.
 */
public final class AdaptClient {
    public static final String DEBUG_HUD_PROPERTY = "adapt.client.debugHud";
    private static final int DEBUG_MARGIN = 4;
    private static final int DEBUG_BACKGROUND = 0x80000000;
    private static final int DEBUG_TEXT = 0xFFFFFFFF;
    private static volatile AdaptClient instance;

    private final UiHost host;
    private final ClientKeys keys;
    private final ClientNet net;
    private final boolean debugHud;
    private final String debugLine = "Adapt client " + AdaptModIdentity.VERSION + " (" + AdaptModIdentity.MINECRAFT_TARGET + ")";
    private final Map<AdaptKey, Runnable> keyActions = new EnumMap<>(AdaptKey.class);
    private final List<Runnable> tickers = new ArrayList<>();
    private final List<Runnable> startTickers = new ArrayList<>();
    private final HudController hud;
    private final InsightHud insight;
    private boolean hudVisible = true;
    private boolean connected;

    AdaptClient(UiHost host, ClientKeys keys, ClientNet net, UiWorld world, boolean debugHud) {
        this.host = Objects.requireNonNull(host, "host");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.net = Objects.requireNonNull(net, "net");
        this.debugHud = debugHud;
        this.hud = new HudController(host, HudLayoutStore.inGameDirectory(), HudClock.SYSTEM, HudSamples.previewRequested());
        keyActions.put(AdaptKey.TOGGLE_HUD, this::toggleHud);
        keyActions.put(AdaptKey.OPEN_HUD_EDITOR, hud::openEditor);
        net.install(net.sinks().withHud(hud.model()));
        net.state().onCatalog(hud.model()::catalog);
        net.state().onPlayer(hud.model()::player);
        this.insight = new InsightHud(host, world, net, System::currentTimeMillis);
        insight.avoid(hud.renderer()::occupied);
    }

    public static AdaptClient initialize(UiHost host, ClientKeys keys, ClientNet net, UiWorld world) {
        AdaptClient client = new AdaptClient(host, keys, net, world, Boolean.getBoolean(DEBUG_HUD_PROPERTY));
        instance = client;
        ClientScreens.install(client);
        if (ItemIndex.viewerPresent()) {
            net.install(net.sinks().withItems(ItemIndex.instance()));
        }
        PanelPreview.installIfRequested(client);
        ScreenPreview.installIfRequested(client);
        IconSheet.installIfRequested(client);
        return client;
    }

    /** The running client, or null on a dedicated server and before the client entry point ran. */
    public static AdaptClient instance() {
        return instance;
    }

    public UiHost host() {
        return host;
    }

    /** The HUD; its model is the session's HUD sink and follows the catalog and player view. */
    public HudController hud() {
        return hud;
    }

    /** Insight's entity readout; its state is the session's Insight sink while it is enabled. */
    public InsightHud insight() {
        return insight;
    }

    /** The {@code adapt:ui} session: state, actions, sinks and the native UI setting. */
    public ClientNet net() {
        return net;
    }

    /** Runs {@code action} on the client thread whenever {@code key} is pressed. */
    public void bindKey(AdaptKey key, Runnable action) {
        keyActions.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(action, "action"));
    }

    /** Runs {@code task} on the client thread at the end of every client tick. */
    public void onTick(Runnable task) {
        tickers.add(Objects.requireNonNull(task, "task"));
    }

    /** Runs {@code task} on the client thread at the start of every client tick, before the player moves. */
    public void onTickStart(Runnable task) {
        startTickers.add(Objects.requireNonNull(task, "task"));
    }

    public void tickStart() {
        for (Runnable ticker : startTickers) {
            ticker.run();
        }
    }

    public void tick() {
        keys.poll(this::pressed);
        hud.tick();
        net.tick();
        for (Runnable ticker : tickers) {
            ticker.run();
        }
    }

    public void connected() {
        connected = true;
        net.connected();
        hud.connected();
    }

    public void disconnected() {
        connected = false;
        net.disconnected();
        hud.disconnected();
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean hudVisible() {
        return hudVisible;
    }

    public void toggleHud() {
        hudVisible = !hudVisible;
    }

    public void renderHud(UiDraw draw, float tickDelta) {
        if (hudVisible) {
            insight.render(draw, tickDelta);
        }
        hud.render(draw, hudVisible);
        if (!hudVisible || !debugHud) {
            return;
        }
        int right = DEBUG_MARGIN + draw.textWidth(debugLine);
        draw.fill(DEBUG_MARGIN - 2, DEBUG_MARGIN - 2, right + 2, DEBUG_MARGIN + draw.lineHeight(), DEBUG_BACKGROUND);
        draw.text(debugLine, DEBUG_MARGIN, DEBUG_MARGIN, DEBUG_TEXT, true);
    }

    private void pressed(AdaptKey key) {
        Runnable action = keyActions.get(key);
        if (action != null) {
            action.run();
        }
    }
}
