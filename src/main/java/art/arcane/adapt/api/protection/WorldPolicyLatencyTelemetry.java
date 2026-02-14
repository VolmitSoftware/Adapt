package art.arcane.adapt.api.protection;

import java.util.ArrayDeque;

public final class WorldPolicyLatencyTelemetry {
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_SAMPLES = 200_000;

    private static final ArrayDeque<Sample> SAMPLES = new ArrayDeque<>();
    private static long totalNanos = 0L;

    private WorldPolicyLatencyTelemetry() {
    }

    public static void recordNanos(long durationNanos) {
        if (durationNanos < 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (SAMPLES) {
            trim(now);
            SAMPLES.addLast(new Sample(now, durationNanos));
            totalNanos += durationNanos;

            while (SAMPLES.size() > MAX_SAMPLES) {
                Sample oldest = SAMPLES.removeFirst();
                totalNanos -= oldest.durationNanos;
            }

            if (totalNanos < 0L) {
                totalNanos = 0L;
            }
        }
    }

    public static double averageMillis(long now) {
        synchronized (SAMPLES) {
            trim(now);
            if (SAMPLES.isEmpty()) {
                return 0D;
            }

            return (totalNanos / 1_000_000D) / (double) SAMPLES.size();
        }
    }

    public static void clear() {
        synchronized (SAMPLES) {
            SAMPLES.clear();
            totalNanos = 0L;
        }
    }

    private static void trim(long now) {
        while (!SAMPLES.isEmpty() && (now - SAMPLES.peekFirst().timestampMs) > WINDOW_MS) {
            Sample oldest = SAMPLES.removeFirst();
            totalNanos -= oldest.durationNanos;
        }

        if (totalNanos < 0L) {
            totalNanos = 0L;
        }
    }

    private record Sample(long timestampMs, long durationNanos) {
    }
}
