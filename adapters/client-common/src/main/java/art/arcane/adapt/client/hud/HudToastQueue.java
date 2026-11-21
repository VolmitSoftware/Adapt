package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ToastKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toasts waiting and on screen. Each card on screen owns one slot, from the moment it slides in until it has fully slid
 * out, so at most {@code maxVisible} cards are drawn and no two ever share a slot; the rest wait. A card stays
 * {@code durationMillis} (challenges twice that), then slides out over {@link #EXIT_MILLIS}; only then does its slot
 * free up, the cards behind it ease forward and the next queued toast slides into the last slot once that is clear.
 * When the cap drops (a smaller screen, the editor's sample), the newest cards over it go back to the front of the queue.
 * <p>
 * A newer toast for the same subject (see {@link HudToast#supersedes}) replaces the waiting one, or the card on screen
 * when that card is not yet leaving, so a burst of level-ups ends on the current level. On the first frame, and after the HUD
 * was not drawn for {@link #PAUSE_MILLIS} (an Adapt screen was open), toasts that waited longer than {@link #STALE_MILLIS}
 * are dropped.
 */
public final class HudToastQueue {
    public static final int MAX_QUEUED = 16;
    public static final long ENTER_MILLIS = 300L;
    public static final long EXIT_MILLIS = 300L;
    public static final long PAUSE_MILLIS = 1000L;
    public static final long STALE_MILLIS = 3000L;
    private static final double SETTLED = 0.05;
    private static final double SLOT_TAU_MILLIS = 70.0;

    private final ArrayDeque<HudToast> queued = new ArrayDeque<>(MAX_QUEUED);
    private final List<HudToast> shown = new ArrayList<>(8);
    private long durationMillis = 5000L;
    private int maxVisible = 3;
    private long updatedAt = Long.MIN_VALUE;
    private boolean instant;

    /** Set from the toast widget's options every frame. */
    public void configure(long duration, int visible, boolean reducedMotion) {
        durationMillis = duration;
        maxVisible = Math.max(1, visible);
        instant = reducedMotion;
    }

    /** How long {@code toast} stays before sliding out. */
    public long duration(HudToast toast) {
        return toast.kind == ToastKind.CHALLENGE ? durationMillis * 2L : durationMillis;
    }

    /** Cards on screen, oldest first; includes cards sliding out, which keep their slot until gone. */
    public List<HudToast> shown() {
        return shown;
    }

    public int queuedCount() {
        return queued.size();
    }

    /** Whether {@code toast} has passed its time and is sliding out. */
    public boolean leaving(HudToast toast, long now) {
        return now - toast.shownAt >= duration(toast);
    }

    void add(HudToast toast, long now) {
        toast.arrivedAt = now;
        for (int index = 0; index < shown.size(); index++) {
            HudToast card = shown.get(index);
            if (toast.supersedes(card) && !leaving(card, now)) {
                toast.slot = card.slot;
                toast.shownAt = now - ENTER_MILLIS;
                shown.set(index, toast);
                return;
            }
        }
        queued.removeIf(toast::supersedes);
        if (queued.size() >= MAX_QUEUED) {
            queued.pollFirst();
        }
        queued.addLast(toast);
    }

    void clear() {
        queued.clear();
        shown.clear();
    }

    void update(long now) {
        long elapsed = updatedAt == Long.MIN_VALUE ? 0L : Math.max(0L, now - updatedAt);
        if (updatedAt == Long.MIN_VALUE || elapsed >= PAUSE_MILLIS) {
            dropStale(now);
        }
        updatedAt = now;
        for (int index = shown.size() - 1; index >= 0; index--) {
            HudToast toast = shown.get(index);
            if (now - toast.shownAt >= duration(toast) + EXIT_MILLIS) {
                shown.remove(index);
            }
        }
        for (int index = shown.size() - 1; index >= 0 && shown.size() > maxVisible; index--) {
            HudToast toast = shown.get(index);
            if (!leaving(toast, now)) {
                shown.remove(index);
                toast.shownAt = Long.MIN_VALUE;
                toast.slot = -1.0;
                queued.addFirst(toast);
            }
        }
        for (int index = 0; index < shown.size(); index++) {
            HudToast toast = shown.get(index);
            double target = index;
            toast.slot = instant ? target : HudMotion.approach(toast.slot, target, elapsed, SLOT_TAU_MILLIS);
            if (index > 0) {
                toast.slot = Math.max(toast.slot, shown.get(index - 1).slot + 1.0);
            }
        }
        while (shown.size() < maxVisible && !queued.isEmpty() && lastSlotClear()) {
            HudToast next = queued.pollFirst();
            next.shownAt = now;
            next.slot = shown.isEmpty() ? 0.0 : Math.max(shown.size(), shown.get(shown.size() - 1).slot + 1.0);
            shown.add(next);
        }
    }

    private boolean lastSlotClear() {
        if (shown.isEmpty()) {
            return true;
        }
        HudToast last = shown.get(shown.size() - 1);
        return last.slot - (shown.size() - 1) <= SETTLED;
    }

    private void dropStale(long now) {
        Iterator<HudToast> waiting = queued.iterator();
        while (waiting.hasNext()) {
            if (now - waiting.next().arrivedAt > STALE_MILLIS) {
                waiting.remove();
            }
        }
    }
}
