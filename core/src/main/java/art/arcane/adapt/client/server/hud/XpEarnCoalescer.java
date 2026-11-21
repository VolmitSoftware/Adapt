package art.arcane.adapt.client.server.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * Limits xp earn events to one per skill every {@link #INTERVAL_MILLIS}; awards inside the interval add up and go
 * out with the next {@link #drain}. Not thread-safe; the owner synchronizes.
 */
public final class XpEarnCoalescer {
  public static final long INTERVAL_MILLIS = 250L;

  private final Map<String, Entry> entries = new HashMap<>();
  private int waiting;

  /** Records an award and returns the amount to send now, or 0 when it waits for the next drain. */
  public double earn(String skillId, double amount, long nowMillis) {
    Entry entry = entries.get(skillId);
    if (entry == null) {
      entry = new Entry(nowMillis - INTERVAL_MILLIS);
      entries.put(skillId, entry);
    }
    if (entry.pending == 0D) {
      waiting++;
    }
    entry.pending += amount;
    return due(entry, nowMillis) ? take(entry, nowMillis) : 0D;
  }

  /** Emits every waiting amount whose interval has passed. */
  public void drain(long nowMillis, Sink sink) {
    if (waiting == 0) {
      return;
    }
    for (Map.Entry<String, Entry> each : entries.entrySet()) {
      Entry entry = each.getValue();
      if (entry.pending != 0D && due(entry, nowMillis)) {
        sink.accept(each.getKey(), take(entry, nowMillis));
      }
    }
  }

  public boolean waiting() {
    return waiting > 0;
  }

  private static boolean due(Entry entry, long nowMillis) {
    return nowMillis - entry.sentAtMillis >= INTERVAL_MILLIS;
  }

  private double take(Entry entry, long nowMillis) {
    double amount = entry.pending;
    entry.pending = 0D;
    entry.sentAtMillis = nowMillis;
    waiting--;
    return amount;
  }

  public interface Sink {
    void accept(String skillId, double amount);
  }

  private static final class Entry {
    private long sentAtMillis;
    private double pending;

    private Entry(long sentAtMillis) {
      this.sentAtMillis = sentAtMillis;
    }
  }
}
