package art.arcane.adapt.client.state;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.Objects;

/**
 * Typed client requests. Each returns false without sending when no session is negotiated or the frame could not be
 * sent; the server validates every request and answers with fresh state or {@code Feedback}.
 */
public final class ClientActions {
    private final ClientState state;
    private final ClientFrameSender sender;

    public ClientActions(ClientState state, ClientFrameSender sender) {
        this.state = Objects.requireNonNull(state, "state");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /** Learns {@code adaptationId} up to {@code level}, as shown in the current catalog. */
    public boolean learn(String adaptationId, int level) {
        CatalogView catalog = state.catalog();
        return catalog != null && send(new ClientFrame.Learn(adaptationId, level, catalog.revision()));
    }

    /** Unlearns {@code adaptationId} down to {@code level}, as shown in the current catalog. */
    public boolean unlearn(String adaptationId, int level) {
        CatalogView catalog = state.catalog();
        return catalog != null && send(new ClientFrame.Unlearn(adaptationId, level, catalog.revision()));
    }

    public boolean setPreference(PreferenceOwner owner, String ownerId, String preferenceId, String value) {
        return send(new ClientFrame.SetPreference(owner, ownerId, preferenceId, value));
    }

    /** Resets every preference of one skill or adaptation. */
    public boolean resetPreference(PreferenceOwner owner, String ownerId) {
        return send(new ClientFrame.ResetPreference(owner, ownerId));
    }

    public boolean open(Route route) {
        return send(new ClientFrame.Open(route));
    }

    public boolean panelClick(int sessionId, int revision, int slot, ClickKind click) {
        return send(new ClientFrame.PanelClick(sessionId, revision, slot, click));
    }

    public boolean panelInput(int sessionId, int revision, int slot, String text) {
        return send(new ClientFrame.PanelInput(sessionId, revision, slot, text));
    }

    public boolean panelClosed(int sessionId) {
        return send(new ClientFrame.PanelClosed(sessionId));
    }

    private boolean send(ClientFrame frame) {
        return state.negotiated() && sender.send(frame);
    }
}
