package art.arcane.adapt.client.ui.kit;

import java.util.Objects;
import java.util.function.LongSupplier;

/** The clock animations read and whether motion is reduced (animations jump straight to their end). */
public final class UiMotion {
    public static final String REDUCED_MOTION_PROPERTY = "adapt.client.reducedMotion";

    private final LongSupplier clock;
    private final boolean reduced;

    public UiMotion(LongSupplier clock, boolean reduced) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reduced = reduced;
    }

    /** Wall clock in milliseconds; reduced when the {@value #REDUCED_MOTION_PROPERTY} system property is true. */
    public static UiMotion system() {
        return new UiMotion(() -> System.nanoTime() / 1_000_000L, Boolean.getBoolean(REDUCED_MOTION_PROPERTY));
    }

    /** Milliseconds on this motion's clock. */
    public long now() {
        return clock.getAsLong();
    }

    public boolean reduced() {
        return reduced;
    }

    /** Progress 0..1 of an animation that started at {@code start} and lasts {@code durationMillis}; 1 when reduced. */
    public float progress(long start, long durationMillis) {
        if (reduced || durationMillis <= 0) {
            return 1.0F;
        }
        return UiEase.clamp((now() - start) / (float) durationMillis);
    }
}
