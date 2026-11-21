package art.arcane.adapt.client.ui.kit;

/** A 0..1 value that eases toward 1 while its condition holds and back to 0 when it stops, such as a hover highlight. */
public final class UiFade {
    private final long durationMillis;
    private boolean target;
    private boolean started;
    private float from;
    private float current;
    private long start;

    public UiFade(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public float update(UiMotion motion, boolean on) {
        long now = motion.now();
        if (!started || on != target) {
            started = true;
            from = current;
            target = on;
            start = now;
        }
        float goal = target ? 1.0F : 0.0F;
        current = UiEase.lerp(from, goal, UiEase.outCubic(motion.progress(start, durationMillis)));
        return current;
    }
}
