package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.event.EventRegistry;
import art.arcane.adapt.api.event.EventSubscription;
import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The subscribed listeners. A starting core binds every one of them to its bus. */
@ApiStatus.Internal
public final class EventSubscriptions implements EventRegistry {
  private final Set<Object> listeners = Collections.newSetFromMap(new IdentityHashMap<>());

  EventSubscriptions() {
  }

  @Override
  public EventSubscription subscribe(Object listener) {
    Objects.requireNonNull(listener, "listener");
    synchronized (this) {
      if (!listeners.add(listener)) {
        throw new IllegalArgumentException("Already subscribed: " + listener.getClass().getName());
      }
    }
    EngineBridge bridge = Engine.current();
    if (bridge != null) {
      bridge.listenerSubscribed(Engine.key(), listener);
    }
    return new Entry(listener);
  }

  @Override
  public <E extends AdaptEvent> E post(E event) {
    requireAddonEvent(event);
    EngineBridge bridge = Engine.current();
    if (bridge != null) {
      bridge.post(event);
    }
    return event;
  }

  /** Throws unless {@code event} is an addon's own type: only Adapt posts the events of the API packages. */
  public static void requireAddonEvent(AdaptEvent event) {
    Objects.requireNonNull(event, "event");
    String type = event.getClass().getName();
    if (type.startsWith("art.arcane.adapt.api.") || type.startsWith("art.arcane.adapt.platform.")) {
      throw new IllegalArgumentException("Only Adapt posts its own events; " + type + " cannot be posted by an addon");
    }
  }

  /** Every subscribed listener, for a core that is starting. */
  public synchronized List<Object> snapshot(HostKey key) {
    Engine.requireHost(key);
    return new ArrayList<>(listeners);
  }

  private synchronized boolean contains(Object listener) {
    return listeners.contains(listener);
  }

  private void remove(Object listener) {
    synchronized (this) {
      if (!listeners.remove(listener)) {
        return;
      }
    }
    EngineBridge bridge = Engine.current();
    if (bridge != null) {
      bridge.listenerUnsubscribed(Engine.key(), listener);
    }
  }

  private final class Entry implements EventSubscription {
    private final Object listener;

    private Entry(Object listener) {
      this.listener = listener;
    }

    @Override
    public Object listener() {
      return listener;
    }

    @Override
    public boolean isActive() {
      return contains(listener);
    }

    @Override
    public void unsubscribe() {
      remove(listener);
    }
  }
}
