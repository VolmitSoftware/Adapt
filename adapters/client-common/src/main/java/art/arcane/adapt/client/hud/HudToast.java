package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;

/** A toast card waiting or on screen. */
public final class HudToast {
    final ToastKind kind;
    final IconRef icon;
    final String title;
    final String body;
    long arrivedAt = Long.MIN_VALUE;
    long shownAt = Long.MIN_VALUE;
    double slot = -1.0;
    String fittedTitle;
    String fittedBody;
    String compactText;
    int compactWidth;

    HudToast(ToastKind kind, IconRef icon, String title, String body) {
        this.kind = kind;
        this.icon = icon;
        this.title = title;
        this.body = body;
    }

    /**
     * Whether this toast makes {@code older} out of date: a level-up for the same skill (or master level, told apart by
     * its icon, which names the skill's sprite), or the same toast again.
     */
    boolean supersedes(HudToast older) {
        if (kind != older.kind || !icon.equals(older.icon)) {
            return false;
        }
        return kind == ToastKind.LEVEL_UP || title.equals(older.title) && body.equals(older.body);
    }

    public ToastKind kind() {
        return kind;
    }

    public IconRef icon() {
        return icon;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    /** The one-line text of the compact pill when it fits: the title, followed by the body when there is one. */
    public String pillText() {
        return body == null || body.isBlank() ? title : title + "  " + body;
    }

    /** Monotonic time the card started showing, or {@link Long#MIN_VALUE} while queued. */
    public long shownAt() {
        return shownAt;
    }

    /** The card's position in the stack as currently drawn, easing toward its index. */
    public double slot() {
        return slot;
    }
}
