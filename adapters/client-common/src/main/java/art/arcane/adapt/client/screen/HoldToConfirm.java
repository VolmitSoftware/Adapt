package art.arcane.adapt.client.screen;

/**
 * A press held for {@link #HOLD_MILLIS} before it counts, for an action that cannot be undone. Letting go early cancels
 * and the fill drains back over {@link #DRAIN_MILLIS}; once it counts it fires once until {@link #reset}.
 */
final class HoldToConfirm {
    static final long HOLD_MILLIS = 1500L;
    static final long DRAIN_MILLIS = 200L;

    private long pressedAt = -1L;
    private long releasedAt = -1L;
    private float releasedFrom;
    private boolean fired;

    /** Starts the hold at {@code now}; a press while already holding or after firing changes nothing. */
    void press(long now) {
        if (pressedAt < 0L && !fired) {
            pressedAt = now;
            releasedAt = -1L;
        }
    }

    /** Lets go at {@code now}: cancels a hold that has not counted yet. */
    void release(long now) {
        if (pressedAt < 0L || fired) {
            return;
        }
        releasedFrom = held(now);
        releasedAt = now;
        pressedAt = -1L;
    }

    boolean holding() {
        return pressedAt >= 0L && !fired;
    }

    boolean fired() {
        return fired;
    }

    /** True exactly once, at the first poll after the hold lasted {@link #HOLD_MILLIS}. */
    boolean poll(long now) {
        if (holding() && now - pressedAt >= HOLD_MILLIS) {
            fired = true;
            return true;
        }
        return false;
    }

    /** How full the control looks at {@code now}, 0 to 1: the hold, a drain after letting go, or full once fired. */
    float shown(long now) {
        if (fired) {
            return 1.0F;
        }
        if (pressedAt >= 0L) {
            return held(now);
        }
        if (releasedAt < 0L) {
            return 0.0F;
        }
        float left = 1.0F - Math.min(1.0F, (now - releasedAt) / (float) DRAIN_MILLIS);
        return releasedFrom * left;
    }

    /** Ready for another hold, as after the server refused the last one. */
    void reset() {
        pressedAt = -1L;
        releasedAt = -1L;
        releasedFrom = 0.0F;
        fired = false;
    }

    private float held(long now) {
        return Math.max(0.0F, Math.min(1.0F, (now - pressedAt) / (float) HOLD_MILLIS));
    }
}
