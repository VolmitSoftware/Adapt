package art.arcane.adapt.client.hud;

/** Time-based easing; every function is frame-rate independent. */
public final class HudMotion {
    private HudMotion() {
    }

    /** 0..1 progress of {@code elapsed} through {@code duration}. */
    public static double progress(long elapsed, long duration) {
        if (duration <= 0L) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) elapsed / (double) duration));
    }

    public static double easeOutCubic(double t) {
        double inverse = 1.0 - t;
        return 1.0 - inverse * inverse * inverse;
    }

    public static double easeInCubic(double t) {
        return t * t * t;
    }

    /** Overshoots slightly, for things that pop in. */
    public static double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        double shifted = t - 1.0;
        return 1.0 + c3 * shifted * shifted * shifted + c1 * shifted * shifted;
    }

    /** Moves {@code value} toward {@code target} exponentially with time constant {@code tauMillis}. */
    public static double approach(double value, double target, long elapsedMillis, double tauMillis) {
        if (elapsedMillis <= 0L) {
            return value;
        }
        double step = 1.0 - Math.exp(-elapsedMillis / tauMillis);
        double next = value + (target - value) * step;
        return Math.abs(target - next) < 0.001 ? target : next;
    }

    /**
     * Visibility of something shown at {@code shownAt} that leaves at {@code leavesAt}: fades in over
     * {@code fadeIn}, out over {@code fadeOut}.
     */
    public static double presence(long now, long shownAt, long leavesAt, long fadeIn, long fadeOut) {
        double in = easeOutCubic(progress(now - shownAt, fadeIn));
        double out = 1.0 - easeInCubic(progress(now - leavesAt, fadeOut));
        return Math.min(in, out);
    }
}
