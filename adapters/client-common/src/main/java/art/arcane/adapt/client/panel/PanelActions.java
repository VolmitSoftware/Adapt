package art.arcane.adapt.client.panel;

import art.arcane.adapt.platform.menu.ClickKind;

/** Where the native panel sends what the player does; the networking side turns these into client frames. */
public interface PanelActions {
    void click(int sessionId, int revision, int slot, ClickKind kind);

    void input(int sessionId, int revision, int slot, String text);

    /** The player closed the panel (Escape, the close button, the inventory key or another screen replacing it). */
    void closed(int sessionId);
}
