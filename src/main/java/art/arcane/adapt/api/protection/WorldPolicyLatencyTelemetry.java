package art.arcane.adapt.api.protection;

import java.util.concurrent.atomic.AtomicLongArray;

public final class WorldPolicyLatencyTelemetry {
  private static final int WINDOW_SECONDS = 60;
  private static final AtomicLongArray SLOT_SECONDS = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray SAMPLE_COUNTS = new AtomicLongArray(WINDOW_SECONDS);
  private static final AtomicLongArray TOTAL_NANOS = new AtomicLongArray(WINDOW_SECONDS);

  private WorldPolicyLatencyTelemetry() {
  }

  public static void recordNanos(long durationNanos) {
    if (durationNanos < 0L) {
      return;
    }

    long epochSecond = System.currentTimeMillis() / 1_000L;
    int slot = (int) (epochSecond % WINDOW_SECONDS);
    prepareSlot(slot, epochSecond);
    SAMPLE_COUNTS.incrementAndGet(slot);
    TOTAL_NANOS.addAndGet(slot, durationNanos);
  }

  public static double averageMillis(long now) {
    long epochSecond = now / 1_000L;
    long samples = 0L;
    long totalNanos = 0L;
    for (int i = 0; i < WINDOW_SECONDS; i++) {
      long slotSecond = SLOT_SECONDS.get(i);
      if (slotSecond <= 0L || epochSecond - slotSecond < 0L || epochSecond - slotSecond >= WINDOW_SECONDS) {
        continue;
      }
      samples += SAMPLE_COUNTS.get(i);
      totalNanos += TOTAL_NANOS.get(i);
    }

    return samples == 0L ? 0D : (totalNanos / 1_000_000D) / (double) samples;
  }

  public static void clear() {
    for (int i = 0; i < WINDOW_SECONDS; i++) {
      SLOT_SECONDS.set(i, 0L);
      SAMPLE_COUNTS.set(i, 0L);
      TOTAL_NANOS.set(i, 0L);
    }
  }

  private static void prepareSlot(int slot, long epochSecond) {
    while (true) {
      long current = SLOT_SECONDS.get(slot);
      if (current == epochSecond) {
        return;
      }
      if (current < 0L) {
        Thread.onSpinWait();
        continue;
      }
      if (!SLOT_SECONDS.compareAndSet(slot, current, -epochSecond)) {
        continue;
      }

      SAMPLE_COUNTS.set(slot, 0L);
      TOTAL_NANOS.set(slot, 0L);
      SLOT_SECONDS.set(slot, epochSecond);
      return;
    }
  }
}
