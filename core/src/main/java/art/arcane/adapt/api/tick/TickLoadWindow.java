package art.arcane.adapt.api.tick;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

final class TickLoadWindow {
  private static final int WINDOW_SECONDS = 60;
  private static final int TAG_BITS = 24;
  private static final int NANOS_BITS = 64 - TAG_BITS;
  private static final long TAG_MASK = (1L << TAG_BITS) - 1L;
  private static final long NANOS_MASK = (1L << NANOS_BITS) - 1L;

  private final AtomicLongArray slots;
  private final AtomicLong startedAtMillis;

  TickLoadWindow(long nowMillis) {
    this.slots = new AtomicLongArray(WINDOW_SECONDS);
    this.startedAtMillis = new AtomicLong(nowMillis);
  }

  void record(long nowMillis, long durationNanos) {
    if (durationNanos <= 0L) {
      return;
    }

    long second = Math.floorDiv(nowMillis, 1_000L);
    int slot = (int) Math.floorMod(second, (long) WINDOW_SECONDS);
    long tag = second & TAG_MASK;
    while (true) {
      long packed = slots.get(slot);
      long base = (packed >>> NANOS_BITS) == tag ? packed & NANOS_MASK : 0L;
      long next = (tag << NANOS_BITS) | Math.min(NANOS_MASK, base + durationNanos);
      if (slots.compareAndSet(slot, packed, next)) {
        return;
      }
    }
  }

  double loadPercent(long nowMillis) {
    long coveredMillis = Math.min(
        nowMillis - startedAtMillis.get(),
        ((WINDOW_SECONDS - 1) * 1_000L) + Math.floorMod(nowMillis, 1_000L)
    );
    if (coveredMillis <= 0L) {
      return 0D;
    }

    long tag = Math.floorDiv(nowMillis, 1_000L) & TAG_MASK;
    long busyNanos = 0L;
    for (int slot = 0; slot < WINDOW_SECONDS; slot++) {
      long packed = slots.get(slot);
      long age = (tag - (packed >>> NANOS_BITS)) & TAG_MASK;
      if (age < WINDOW_SECONDS) {
        busyNanos += packed & NANOS_MASK;
      }
    }

    double percent = ((busyNanos / 1_000_000D) / coveredMillis) * 100D;
    return Double.isFinite(percent) ? Math.max(0D, percent) : 0D;
  }

  void reset(long nowMillis) {
    for (int slot = 0; slot < WINDOW_SECONDS; slot++) {
      slots.set(slot, 0L);
    }
    startedAtMillis.set(nowMillis);
  }
}
