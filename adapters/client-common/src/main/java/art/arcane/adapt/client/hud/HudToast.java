package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.view.IconRef;

/** A toast card waiting or on screen. */
public final class HudToast {
    final ToastKind kind;
    final IconRef icon;
    final String title;
    final String body;
    final String skillId;
    long arrivedAt = Long.MIN_VALUE;
    long shownAt = Long.MIN_VALUE;
    long stay;
    double offset = -1.0;
    int height = ToastWidget.CARD_HEIGHT;
    int lines;
    int laidOutWidest = -1;
    int laidOutStretch = -1;
    int laidOutLines = -1;
    int cardWidth;
    int cardHeight;
    String shownTitle;
    String shownBody;
    int titleWrap;
    int bodyWrap;
    int titleLines;
    int bodyLines;
    String compactText;
    int compactWidth;
    ToastLook look;
    PageTheme lookTheme;

    HudToast(ToastKind kind, IconRef icon, String title, String body, String skillId) {
        this.kind = kind;
        this.icon = icon;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.skillId = skillId == null ? "" : skillId;
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

    /** The skill whose look the card takes; empty for none. */
    public String skillId() {
        return skillId;
    }

    /** The one-line text of the compact pill when it fits: the title, followed by the body when there is one. */
    public String pillText() {
        return body.isBlank() ? title : title + "  " + body;
    }

    /** Monotonic time the card started showing, or {@link Long#MIN_VALUE} while queued. */
    public long shownAt() {
        return shownAt;
    }

    /** Distance in units from the stack's first edge to this card as currently drawn, easing toward its place. */
    public double offset() {
        return offset;
    }

    /** Height in units of the card as last laid out. */
    public int height() {
        return height;
    }

    /** Width in units of the card as last laid out. */
    public int width() {
        return cardWidth;
    }

    /** Title and body lines of the card as last laid out; zero before its first layout. */
    public int lines() {
        return lines;
    }

    /** The title as drawn: the whole title unless it was too long for its lines. */
    public String shownTitle() {
        return shownTitle;
    }

    /** The body as drawn: the whole body unless it was too long for its lines. */
    public String shownBody() {
        return shownBody;
    }
}
