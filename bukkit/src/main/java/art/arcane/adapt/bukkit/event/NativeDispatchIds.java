package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.bukkit.event.Event;

import java.lang.ref.WeakReference;

/**
 * Maps a native Bukkit event object to one core dispatch id, so the core events the bridge builds for
 * each priority of that native event share {@link AdaptEvent#dispatchId()}. Bukkit fires every priority
 * of an event on the thread that called it, so a short per-thread ring of recent events (matched by
 * identity) covers nested events fired from handlers.
 */
final class NativeDispatchIds {
  private static final int SIZE = 32;
  private static final ThreadLocal<NativeDispatchIds> LOCAL = ThreadLocal.withInitial(NativeDispatchIds::new);

  @SuppressWarnings("unchecked")
  private final WeakReference<Event>[] events = (WeakReference<Event>[]) new WeakReference<?>[SIZE];
  private final long[] ids = new long[SIZE];
  private int next;

  private NativeDispatchIds() {
  }

  static long of(Event event) {
    return LOCAL.get().lookup(event);
  }

  private long lookup(Event event) {
    for (int i = 0; i < SIZE; i++) {
      WeakReference<Event> reference = events[i];
      if (reference != null && reference.get() == event) {
        return ids[i];
      }
    }
    long id = AdaptEvent.newDispatchId();
    events[next] = new WeakReference<>(event);
    ids[next] = id;
    next = (next + 1) % SIZE;
    return id;
  }
}
