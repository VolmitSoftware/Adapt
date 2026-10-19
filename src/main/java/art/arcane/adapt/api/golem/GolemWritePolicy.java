package art.arcane.adapt.api.golem;

public final class GolemWritePolicy {
  public static final long DEBOUNCE_MILLIS = 5_000L;
  private static final long MILLIS_PER_TICK = 50L;

  private boolean dirty;
  private long dirtySince;

  public static int ticks(long millis) {
    return (int) Math.max(1L, (millis + MILLIS_PER_TICK - 1L) / MILLIS_PER_TICK);
  }

  public synchronized boolean markDirty(long nowMillis) {
    if (dirty) {
      return false;
    }
    dirty = true;
    dirtySince = nowMillis;
    return true;
  }

  public synchronized boolean isDirty() {
    return dirty;
  }

  public synchronized boolean isDue(long nowMillis) {
    return dirty && nowMillis - dirtySince >= DEBOUNCE_MILLIS;
  }

  public synchronized long remainingMillis(long nowMillis) {
    return dirty ? Math.max(0L, DEBOUNCE_MILLIS - (nowMillis - dirtySince)) : 0L;
  }

  public synchronized void flushed() {
    dirty = false;
  }
}
