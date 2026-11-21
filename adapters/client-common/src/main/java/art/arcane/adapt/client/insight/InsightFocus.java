package art.arcane.adapt.client.insight;

/**
 * How far the target's plate is expanded, 0 (a slim bar) to 1 (the full readout). Eases toward the crosshair state
 * in {@link #DURATION_MILLIS} and starts collapsed for every new target.
 */
public final class InsightFocus {
    static final long DURATION_MILLIS = 140L;

    private int entityId = Integer.MIN_VALUE;
    private double value;
    private long lastAt = Long.MIN_VALUE;

    public double advance(int targetId, boolean focused, long nowMillis) {
        if (targetId != entityId) {
            entityId = targetId;
            value = 0D;
            lastAt = nowMillis;
        }
        long elapsed = lastAt == Long.MIN_VALUE ? 0L : Math.max(0L, nowMillis - lastAt);
        lastAt = nowMillis;
        double step = elapsed / (double) DURATION_MILLIS;
        value = focused ? Math.min(1D, value + step) : Math.max(0D, value - step);
        return eased();
    }

    /** The expansion with ease-out applied. */
    public double eased() {
        double inverse = 1D - value;
        return 1D - inverse * inverse * inverse;
    }
}
