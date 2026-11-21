package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiScreen;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiTheme;

import java.util.Objects;

/**
 * Opens, updates and closes native panels for the server's panel frames. Call from the client thread. Closes the
 * server asked for (and panels replaced by a new session) are not reported back through {@link PanelActions#closed}.
 */
public final class PanelScreens {
    private final UiHost host;
    private final PanelActions actions;
    private final UiTheme theme;
    private final UiMotion motion;

    public PanelScreens(UiHost host, PanelActions actions) {
        this(host, actions, UiTheme.DEFAULT, UiMotion.system());
    }

    public PanelScreens(UiHost host, PanelActions actions, UiTheme theme, UiMotion motion) {
        this.host = Objects.requireNonNull(host, "host");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.motion = Objects.requireNonNull(motion, "motion");
    }

    /** Shows {@code view}; a panel of the same session already open is updated in place instead. */
    public void open(PanelView view) {
        NativePanelScreen current = current();
        if (current != null && current.sessionId() == view.sessionId()) {
            current.apply(view);
            return;
        }
        if (current != null) {
            current.markSilent();
        }
        host.open(new NativePanelScreen(view, actions, theme, motion));
    }

    /** Updates the open panel of {@code view}'s session; ignored when that panel is no longer open. */
    public void update(PanelView view) {
        NativePanelScreen current = current();
        if (current != null && current.sessionId() == view.sessionId()) {
            current.apply(view);
        }
    }

    /** Closes the panel of {@code sessionId} if it is open, without reporting the close to the server. */
    public void close(int sessionId) {
        NativePanelScreen current = current();
        if (current != null && current.sessionId() == sessionId) {
            current.closeSilently();
        }
    }

    /** Closes any open panel without reporting it, for a disconnect or a withdrawn capability. */
    public void reset() {
        NativePanelScreen current = current();
        if (current != null) {
            current.closeSilently();
        }
    }

    /** The open native panel, or null. */
    public NativePanelScreen current() {
        UiScreen screen = host.current();
        return screen instanceof NativePanelScreen panel ? panel : null;
    }
}
