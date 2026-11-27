package art.arcane.adapt.client.net;

import art.arcane.adapt.client.config.ClientConfig;
import art.arcane.adapt.client.config.UiSettings;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameFragments;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.state.ClientActions;
import art.arcane.adapt.client.state.ClientState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * The client end of {@code adapt:ui}. Once the server registered the channel it sends one {@code Hello} per
 * connection, decodes what comes back into {@link ClientState} and the installed {@link ClientSinks}, withdraws with
 * {@code Hello(0)} when the player turns the native UI off, and drops the session on a malformed frame. Every method
 * runs on the client thread.
 */
public final class ClientNet {
    private static final Logger LOGGER = LoggerFactory.getLogger("Adapt");

    private final ClientTransport transport;
    private final ClientConfig config;
    private final ClientState state = new ClientState();
    private final FrameFragments.Assembler assembler = new FrameFragments.Assembler(FrameLimits.MAX_S2C_BYTES);
    private final ClientActions actions;
    private ClientSinks sinks = ClientSinks.NONE;
    private boolean connected;
    private boolean helloSent;
    private int helloMask;
    private int offered;
    private boolean dropped;

    public ClientNet(ClientTransport transport, ClientConfig config) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.config = Objects.requireNonNull(config, "config");
        this.actions = new ClientActions(state, this::write);
    }

    /** Replaces the sinks; a changed capability set renegotiates on the next tick. */
    public void install(ClientSinks installed) {
        sinks = Objects.requireNonNull(installed, "installed");
        renegotiateIfChanged();
    }

    /** Offers capability bits that need no sink, such as {@link ClientProtocol#JUMPS}; a change renegotiates. */
    public void offer(int capabilityBits) {
        offered |= capabilityBits;
        renegotiateIfChanged();
    }

    /** Reports the jump key to a server that asked for it; false when nothing was sent. */
    public boolean jumpKey(boolean down) {
        return state.negotiated() && state.has(ClientProtocol.JUMPS) && write(new ClientFrame.JumpKey(down));
    }

    private int capabilities() {
        return sinks.capabilities() | offered;
    }

    private void renegotiateIfChanged() {
        if (helloSent && !dropped && capabilities() != helloMask) {
            helloSent = false;
        }
    }

    public ClientSinks sinks() {
        return sinks;
    }

    public ClientState state() {
        return state;
    }

    public ClientActions actions() {
        return actions;
    }

    public ClientConfig config() {
        return config;
    }

    public boolean nativeUi() {
        return config.ui().nativeUi();
    }

    /** Saves the setting; turning it off withdraws at once, turning it on negotiates on the next tick. */
    public void nativeUi(boolean enabled) {
        UiSettings ui = config.ui();
        if (ui.nativeUi() == enabled) {
            return;
        }
        ui.nativeUi(enabled);
        config.save();
        if (!enabled && helloSent) {
            if (transport.ready()) {
                write(new ClientFrame.Hello(0, 0));
            }
            endSession();
            helloSent = false;
        }
    }

    public void connected() {
        reset();
        connected = true;
    }

    public void disconnected() {
        reset();
        connected = false;
    }

    public void tick() {
        if (!connected || helloSent || dropped || !config.ui().nativeUi() || !transport.ready()) {
            return;
        }
        int mask = capabilities();
        if (write(new ClientFrame.Hello(ClientProtocol.VERSION, mask))) {
            helloSent = true;
            helloMask = mask;
        }
    }

    /**
     * One clientbound payload, in packet order: a frame or a fragment of one. Frames outside a session this client
     * opened are ignored.
     */
    public void receive(byte[] bytes) {
        if (!connected || !helloSent || dropped) {
            return;
        }
        ServerFrame frame;
        try {
            byte[] whole = assembler.accept(bytes);
            if (whole == null) {
                return;
            }
            frame = FrameCodec.decodeServer(whole);
        } catch (ClientProtocolException exception) {
            drop(exception.getMessage());
            return;
        }
        if (frame instanceof ServerFrame.Accept accept) {
            if (accept.version() != ClientProtocol.VERSION) {
                drop("server accepted unsupported version " + accept.version());
                return;
            }
            state.negotiated(accept.version(), accept.server());
            return;
        }
        if (!state.negotiated()) {
            return;
        }
        try {
            dispatch(frame);
        } catch (RuntimeException failure) {
            LOGGER.error("Adapt client could not handle {}", frame.getClass().getSimpleName(), failure);
        }
    }

    private void dispatch(ServerFrame frame) {
        switch (frame) {
            case ServerFrame.Accept accept -> state.negotiated(accept.version(), accept.server());
            case ServerFrame.Catalog catalog -> state.acceptCatalog(catalog.catalog());
            case ServerFrame.Player player -> state.acceptPlayer(player.player());
            case ServerFrame.PlayerUpdate update -> state.applyDelta(update.delta());
            case ServerFrame.OpenRoute open -> sinks.routes().open(open.route());
            case ServerFrame.PanelOpen open -> sinks.panels().open(open.panel());
            case ServerFrame.PanelUpdate update -> sinks.panels().update(update.panel());
            case ServerFrame.PanelClose close -> sinks.panels().close(close.sessionId());
            case ServerFrame.HudPublish publish -> sinks.hud().publish(publish);
            case ServerFrame.HudClear clear -> sinks.hud().clear(clear.purpose());
            case ServerFrame.HudClearAll ignored -> sinks.hud().clearAll();
            case ServerFrame.HudStatus status -> sinks.hud().status(status);
            case ServerFrame.XpEarned earned -> sinks.hud().xp(earned);
            case ServerFrame.Toast toast -> sinks.hud().toast(toast);
            case ServerFrame.CustomItems items -> {
                state.acceptItems(items.items());
                sinks.items().items(items.items());
            }
            case ServerFrame.Feedback feedback -> sinks.feedback().feedback(feedback.kind(), feedback.text());
            case ServerFrame.InsightTarget target -> sinks.insight().target(target);
            case ServerFrame.InsightVitals vitals -> sinks.insight().vitals(vitals.entityId(), vitals.health(), vitals.absorption());
            case ServerFrame.InsightMarks marks -> sinks.insight().marks(marks.marks());
            case ServerFrame.InsightClear ignored -> sinks.insight().clear();
            case ServerFrame.MutateOffers offers -> state.acceptMutateOffers(offers.skillIds());
            case ServerFrame.MutationKept kept -> state.acceptKept(kept.title(), kept.kept());
            case ServerFrame.MutationTools tools -> state.acceptTools(tools.tools());
            case ServerFrame.MutationSpread spread -> state.acceptSpread(spread.spread());
            case ServerFrame.XpGuides guides -> state.acceptXpGuides(guides.title(), guides.guides());
            case ServerFrame.Spending spending -> state.acceptSpending(spending.inactiveSkills(), spending.spends());
            case ServerFrame.GroupLabels labels -> state.acceptGroupLabels(labels.skills());
            case ServerFrame.ShortNames names -> state.acceptShortNames(names.names());
            case ServerFrame.AdaptationRecipes recipes -> state.acceptRecipes(recipes.recipes());
            case ServerFrame.AssetManifest manifest -> request(sinks.assets().manifest(manifest.assets()));
            case ServerFrame.Asset asset -> sinks.assets().asset(asset.resource(), asset.hash(), asset.data());
        }
    }

    /** Asks for {@code resources} a bounded batch per frame. */
    private void request(List<String> resources) {
        for (int from = 0; from < resources.size(); from += FrameLimits.MAX_ASSET_REQUEST) {
            write(new ClientFrame.AssetRequest(resources.subList(from, Math.min(resources.size(), from + FrameLimits.MAX_ASSET_REQUEST))));
        }
    }

    private boolean write(ClientFrame frame) {
        if (!transport.ready()) {
            return false;
        }
        List<byte[]> payloads;
        try {
            payloads = FrameFragments.split(FrameCodec.encodeClient(frame), FrameLimits.MAX_C2S_PAYLOAD_BYTES);
        } catch (ClientProtocolException exception) {
            LOGGER.warn("Adapt client could not encode {}: {}", frame.getClass().getSimpleName(), exception.getMessage());
            return false;
        }
        for (byte[] payload : payloads) {
            transport.send(payload);
        }
        return true;
    }

    private void drop(String reason) {
        LOGGER.warn("Adapt client UI disabled for this connection: {}", reason);
        if (transport.ready()) {
            write(new ClientFrame.Hello(0, 0));
        }
        endSession();
        dropped = true;
    }

    private void endSession() {
        assembler.reset();
        ClientSinks current = sinks;
        current.panels().reset();
        current.hud().reset();
        current.items().reset();
        current.insight().reset();
        current.assets().reset();
        state.reset();
    }

    private void reset() {
        endSession();
        helloSent = false;
        helloMask = 0;
        dropped = false;
    }
}
