package art.arcane.adapt.client.hud;

import art.arcane.adapt.api.notification.HudSlot;

/** A text segment from a {@code HudPublish} frame. {@code text} keeps its legacy section colour codes. */
public final class HudNotice {
    final String purpose;
    int priority;
    HudSlot slot;
    String text;
    long shownAt;
    long expiresAt;
    String compactSource;
    String compactText;
    int compactWidth;

    HudNotice(String purpose) {
        this.purpose = purpose;
    }

    public String purpose() {
        return purpose;
    }

    public int priority() {
        return priority;
    }

    /** The first slot the server asked for; decides the line's alignment. */
    public HudSlot slot() {
        return slot;
    }

    public String text() {
        return text;
    }

    public long shownAt() {
        return shownAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    /** Titles and menu titles are drawn larger than ambient lines. */
    public boolean emphasized() {
        return purpose.equals("adapt:title") || purpose.equals("adapt:gui");
    }
}
