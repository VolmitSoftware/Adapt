package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.view.IconRef;

/**
 * One glyph from a {@code HudStatus} frame, timed on the monotonic clock. Remembers when its pips last changed and
 * when it filled up, so the widget can flash the pip that moved and glow once at full.
 */
public final class HudStatusEntry {
    /** How long a finished cooldown stays to pulse "ready" before it leaves. */
    public static final long READY_MILLIS = 450L;

    String purpose;
    HudGlyph glyph = HudGlyph.COOLDOWN;
    String skillId = "";
    IconRef icon;
    String label;
    boolean lit;
    int stacks;
    int maxStacks;
    int previousStacks;
    long stacksChangedAt = Long.MIN_VALUE;
    long fullAt = Long.MIN_VALUE;
    long startsAt;
    long endsAt;
    long durationMillis;
    long receivedAt;
    long shownAt;
    String countText = "0";
    String animOwner;
    double animX;
    double animY;
    long animAt;
    private boolean seen;
    private boolean appeared;

    HudStatusEntry(String purpose) {
        this.purpose = purpose;
    }

    public String purpose() {
        return purpose;
    }

    public HudGlyph glyph() {
        return glyph;
    }

    /** The skill this glyph belongs to, empty when none. */
    public String skillId() {
        return skillId;
    }

    public IconRef icon() {
        return icon;
    }

    public String label() {
        return label;
    }

    public boolean lit() {
        return lit;
    }

    public int stacks() {
        return stacks;
    }

    /** The most stacks this status can reach, or 0 when it has no cap. */
    public int maxStacks() {
        return maxStacks;
    }

    /** The stacks as text, rebuilt only when they change. */
    public String countText() {
        return countText;
    }

    /** Stacks before the last change. */
    public int previousStacks() {
        return previousStacks;
    }

    /** Monotonic time the stacks last changed, or {@link Long#MIN_VALUE}. */
    public long stacksChangedAt() {
        return stacksChangedAt;
    }

    /** Monotonic time the stacks last reached the cap, or {@link Long#MIN_VALUE}. */
    public long fullAt() {
        return fullAt;
    }

    public boolean full() {
        return maxStacks > 0 && stacks >= maxStacks;
    }

    public boolean openEnded() {
        return durationMillis <= 0L;
    }

    public long receivedAt() {
        return receivedAt;
    }

    /** Monotonic time the glyph first appeared; later updates keep it. */
    public long shownAt() {
        return shownAt;
    }

    public long endsAt() {
        return endsAt;
    }

    public long remaining(long now) {
        return openEnded() ? Long.MAX_VALUE : Math.max(0L, endsAt - now);
    }

    /** 1 when it just started, 0 when it ends; 1 for open-ended states. */
    public double fractionLeft(long now) {
        if (openEnded()) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) remaining(now) / (double) durationMillis));
    }

    /** Whether this is a cooldown that has run out and is pulsing before it leaves. */
    public boolean ready(long now) {
        return glyph == HudGlyph.COOLDOWN && !openEnded() && now >= endsAt;
    }

    /** 0..1 through the ready pulse; 0 before the cooldown ends. */
    public double readyProgress(long now) {
        return ready(now) ? HudMotion.progress(now - endsAt, READY_MILLIS) : 0.0;
    }

    /** Whether the model should drop this entry at {@code now}. */
    boolean expired(long now) {
        return switch (glyph) {
            case COOLDOWN -> !openEnded() && now >= endsAt + READY_MILLIS;
            case DURATION -> !openEnded() && now >= endsAt;
            case CHARGES, TOGGLE -> false;
        };
    }

    /**
     * Hands this glyph's place to another glyph of the same owner: it keeps its first appearance and its drawn position,
     * so the new kind takes the slot without popping in, and forgets the pip history of the old kind.
     */
    void takeOver(String newPurpose) {
        purpose = newPurpose;
        seen = false;
        stacksChangedAt = Long.MIN_VALUE;
        fullAt = Long.MIN_VALUE;
    }

    /** Whether both purposes name the same owner, as {@code <owner>.<kind>} does for each glyph of one adaptation. */
    static boolean sameOwner(String left, String right) {
        int dot = left.lastIndexOf('.');
        return dot > 0 && right.lastIndexOf('.') == dot && left.regionMatches(0, right, 0, dot);
    }

    void set(HudGlyph newGlyph, String newSkillId, IconRef newIcon, String newLabel, boolean newLit, int newStacks,
             int newMaxStacks, long newStartsAt, long newDuration, long now) {
        boolean known = seen;
        seen = true;
        if (!appeared) {
            appeared = true;
            shownAt = now;
        }
        glyph = newGlyph;
        skillId = newSkillId;
        icon = newIcon;
        label = newLabel;
        lit = newLit;
        if (!known) {
            previousStacks = newStacks;
        } else if (newStacks != stacks) {
            previousStacks = stacks;
            stacksChangedAt = now;
        }
        if (newMaxStacks > 0 && newStacks >= newMaxStacks && (!known || stacks < newMaxStacks || maxStacks != newMaxStacks)) {
            fullAt = now;
        }
        if (newStacks != stacks || !known) {
            countText = Integer.toString(newStacks);
        }
        stacks = newStacks;
        maxStacks = newMaxStacks;
        durationMillis = Math.max(0L, newDuration);
        startsAt = newStartsAt;
        endsAt = durationMillis <= 0L ? Long.MAX_VALUE : newStartsAt + durationMillis;
        receivedAt = now;
    }
}
