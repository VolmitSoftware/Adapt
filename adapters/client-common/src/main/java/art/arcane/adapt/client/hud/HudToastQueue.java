package art.arcane.adapt.client.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toasts waiting and on screen. Each card on screen owns its place in the stack, as tall as the card, from the moment it
 * slides in until it has fully slid out, so at most {@code maxVisible} cards are drawn and no two ever overlap; the rest
 * wait, as does a card that would make the stack taller than {@code room} units. A card is on screen for
 * {@code durationMillis} in all, its slide out included, whatever its length. While more toasts wait than there are
 * free places (a backlog), it stays {@code durationMillis / (1 + }{@link #BACKLOG_SHARE}{@code  x backlog)}, never under
 * {@link #MIN_STAY_MILLIS}, and a card already up shortens when a backlog builds. Only once a card has slid out does its place free up, the cards behind
 * it ease forward and the next queued toast slides in behind the last card once that has settled. When the cap drops (a
 * smaller screen, the editor's sample), the newest cards over it go back to the front of the queue.
 * <p>
 * A newer toast for the same subject (see {@link HudToast#supersedes}) replaces the waiting one, or the card on screen
 * when that card is not yet leaving, so a burst of level-ups ends on the current level. On the first frame, and after the HUD
 * was not drawn for {@link #PAUSE_MILLIS} (an Adapt screen was open), toasts that waited longer than {@link #STALE_MILLIS}
 * are dropped.
 */
public final class HudToastQueue {
    public static final int MAX_QUEUED = 16;
    public static final long ENTER_MILLIS = 240L;
    public static final long EXIT_MILLIS = 240L;
    public static final long PAUSE_MILLIS = 1000L;
    public static final long STALE_MILLIS = 3000L;
    public static final long MIN_STAY_MILLIS = 1000L;
    public static final double BACKLOG_SHARE = 1.0;
    /** Units between stacked cards. */
    public static final int GAP = 4;
    private static final double SETTLED = 0.5;
    private static final double SLOT_TAU_MILLIS = 70.0;

    private final ArrayDeque<HudToast> queued = new ArrayDeque<>(MAX_QUEUED);
    private final List<HudToast> shown = new ArrayList<>(8);
    private long durationMillis = 3000L;
    private int maxVisible = 3;
    private double room = Double.MAX_VALUE;
    private long updatedAt = Long.MIN_VALUE;
    private boolean instant;

    /** Set from the toast widget's options every frame; {@code stackRoom} is the tallest stack in units. */
    public void configure(long duration, int visible, double stackRoom, boolean reducedMotion) {
        durationMillis = duration;
        maxVisible = Math.max(1, visible);
        room = stackRoom;
        instant = reducedMotion;
    }

    /** How long {@code toast} stays before sliding out: as decided when it came on screen, or shortened since. */
    public long duration(HudToast toast) {
        return toast.stay > 0L ? toast.stay : stay(backlog());
    }

    /** Toasts waiting beyond the places free on screen. */
    private int backlog() {
        return Math.max(0, queued.size() - Math.max(0, maxVisible - shown.size()));
    }

    /** How long a card stays before sliding out while {@code backlog} toasts wait. */
    long stay(int backlog) {
        long whole = Math.round(durationMillis / (1.0 + BACKLOG_SHARE * backlog));
        return Math.max(Math.min(durationMillis, MIN_STAY_MILLIS), whole) - EXIT_MILLIS;
    }

    /** Cards on screen, oldest first; includes cards sliding out, which keep their slot until gone. */
    public List<HudToast> shown() {
        return shown;
    }

    public int queuedCount() {
        return queued.size();
    }

    /** The toast that goes on screen next, laid out ahead so the stack knows whether it fits; null when none waits. */
    HudToast waiting() {
        return queued.peekFirst();
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
                toast.offset = card.offset;
                toast.shownAt = now - ENTER_MILLIS;
                toast.stay = stay(backlog()) + ENTER_MILLIS;
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
                toast.stay = 0L;
                toast.offset = -1.0;
                queued.addFirst(toast);
            }
        }
        long rushed = stay(backlog());
        for (int index = 0; index < shown.size(); index++) {
            HudToast toast = shown.get(index);
            if (rushed < toast.stay && !leaving(toast, now)) {
                toast.stay = Math.max(rushed, now - toast.shownAt);
            }
        }
        double target = 0.0;
        double lastTarget = 0.0;
        for (int index = 0; index < shown.size(); index++) {
            HudToast toast = shown.get(index);
            toast.offset = instant ? target : HudMotion.approach(toast.offset, target, elapsed, SLOT_TAU_MILLIS);
            if (index > 0) {
                HudToast ahead = shown.get(index - 1);
                toast.offset = Math.max(toast.offset, ahead.offset + ahead.height + GAP);
            }
            lastTarget = target;
            target += toast.height + GAP;
        }
        while (shown.size() < maxVisible && !queued.isEmpty() && settled(lastTarget)
                && (shown.isEmpty() || target + queued.peekFirst().height <= room)) {
            HudToast next = queued.pollFirst();
            next.shownAt = now;
            if (shown.isEmpty()) {
                next.offset = 0.0;
            } else {
                HudToast last = shown.get(shown.size() - 1);
                next.offset = Math.max(target, last.offset + last.height + GAP);
            }
            shown.add(next);
            next.stay = stay(backlog());
            lastTarget = target;
            target += next.height + GAP;
        }
    }

    private boolean settled(double lastTarget) {
        return shown.isEmpty() || shown.get(shown.size() - 1).offset - lastTarget <= SETTLED;
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
