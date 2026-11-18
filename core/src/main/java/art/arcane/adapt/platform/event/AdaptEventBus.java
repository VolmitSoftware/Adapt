package art.arcane.adapt.platform.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Adapt's gameplay event bus.
 *
 * <p>Ordering: handlers run by {@link EventPriority} (LOWEST first), then in registration order. A
 * handler registered for a supertype (for example the entity damage event) also receives subtypes
 * (the by-entity damage event), merged into the same order, as Bukkit does.
 *
 * <p>Cancellation: a handler with {@code ignoreCancelled} is skipped while the event reports
 * cancelled at the moment that handler's turn comes.
 *
 * <p>Errors: a throwing handler is reported to the {@link EventErrorHandler} and the remaining
 * handlers still run. {@link VirtualMachineError}s are rethrown.
 *
 * <p>Threading: registration is synchronised; posting is lock-free and may happen on several threads
 * at once (Folia regions). Posting allocates nothing once the handler table for an event class is
 * built; the table is rebuilt lazily after registrations change.
 */
public final class AdaptEventBus {
  private static final Registration[] NO_HANDLERS = new Registration[0];
  private static final int PRIORITY_COUNT = EventPriority.values().length;
  private static final Comparator<Registration> ORDER = Comparator
      .comparingInt((Registration registration) -> registration.priority.ordinal())
      .thenComparingLong(registration -> registration.sequence);

  private final Object lock = new Object();
  private final Map<Class<?>, List<Registration>> byType = new HashMap<>();
  private final Map<Object, List<Registration>> byOwner = new IdentityHashMap<>();
  private final ConcurrentHashMap<Class<?>, Table> tables = new ConcurrentHashMap<>();
  private final List<Consumer<Class<? extends AdaptEvent>>> registrationListeners = new CopyOnWriteArrayList<>();
  private volatile EventErrorHandler errorHandler;
  private long nextSequence;

  public AdaptEventBus(EventErrorHandler errorHandler) {
    this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
  }

  public void setErrorHandler(EventErrorHandler handler) {
    this.errorHandler = Objects.requireNonNull(handler, "handler");
  }

  public <E extends AdaptEvent> Subscription register(Object owner, Class<E> type, EventPriority priority,
                                                      boolean ignoreCancelled, EventListener<? super E> listener) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(listener, "listener");
    @SuppressWarnings("unchecked")
    EventListener<Object> erased = (EventListener<Object>) (EventListener<?>) listener;
    Registration registration;
    synchronized (lock) {
      registration = new Registration(this, owner, type, priority, ignoreCancelled, erased, nextSequence++);
      byType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(registration);
      byOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(registration);
      tables.clear();
    }
    notifyRegistration(type);
    return registration;
  }

  /** Registers with {@code ignoreCancelled = true}, Adapt's default for cancellable events. */
  public <E extends AdaptEvent> Subscription register(Object owner, Class<E> type, EventPriority priority,
                                                      EventListener<? super E> listener) {
    return register(owner, type, priority, true, listener);
  }

  public <E extends AdaptEvent> Subscription register(Object owner, Class<E> type, EventListener<? super E> listener) {
    return register(owner, type, EventPriority.NORMAL, true, listener);
  }

  /** Removes every handler the owner registered. Returns how many were removed. */
  public int unregisterAll(Object owner) {
    List<Registration> owned;
    synchronized (lock) {
      owned = byOwner.remove(owner);
      if (owned == null) {
        return 0;
      }
      for (Registration registration : owned) {
        registration.active = false;
        removeFromType(registration);
      }
      tables.clear();
    }
    List<Class<? extends AdaptEvent>> notified = new ArrayList<>();
    for (Registration registration : owned) {
      if (!notified.contains(registration.type)) {
        notified.add(registration.type);
        notifyRegistration(registration.type);
      }
    }
    return owned.size();
  }

  public void clear() {
    synchronized (lock) {
      for (List<Registration> registrations : byType.values()) {
        for (Registration registration : registrations) {
          registration.active = false;
        }
      }
      byType.clear();
      byOwner.clear();
      tables.clear();
    }
    notifyRegistration(AdaptEvent.class);
  }

  /**
   * Called after handlers for an event type are added or removed, outside the bus lock, on the thread
   * that changed them. {@link #clear()} reports {@link AdaptEvent}. A bridge uses it to subscribe to
   * native events only while some handler would receive them: every posted class assignable to the
   * reported type may have a new {@link #priorityMask}.
   */
  public void addRegistrationListener(Consumer<Class<? extends AdaptEvent>> listener) {
    registrationListeners.add(Objects.requireNonNull(listener, "listener"));
  }

  public void removeRegistrationListener(Consumer<Class<? extends AdaptEvent>> listener) {
    registrationListeners.remove(listener);
  }

  public Collection<Subscription> subscriptions(Object owner) {
    synchronized (lock) {
      List<Registration> owned = byOwner.get(owner);
      return owned == null ? List.of() : List.copyOf(owned);
    }
  }

  /** True when a handler would receive an event of exactly this class. Bridges use it to skip translation. */
  public boolean hasListeners(Class<? extends AdaptEvent> eventClass) {
    return table(eventClass).handlers.length > 0;
  }

  public boolean hasListeners(Class<? extends AdaptEvent> eventClass, EventPriority priority) {
    return (table(eventClass).priorityMask & priority.bit()) != 0;
  }

  /** Bit set of priorities with handlers for this class, bit {@code n} for {@code EventPriority.values()[n]}. */
  public int priorityMask(Class<? extends AdaptEvent> eventClass) {
    return table(eventClass).priorityMask;
  }

  /**
   * Bit set of priorities with at least one handler that also receives cancelled events. A bridge
   * skips translating an already cancelled native event at a priority whose bit is clear.
   */
  public int receivesCancelledMask(Class<? extends AdaptEvent> eventClass) {
    return table(eventClass).receivesCancelledMask;
  }

  /** Dispatches to every priority. Returns the event for chaining. */
  public <E extends AdaptEvent> E post(E event) {
    Table table = table(event.getClass());
    dispatch(table.handlers, 0, table.handlers.length, event);
    return event;
  }

  /**
   * Dispatches only the handlers of one priority. A Bukkit bridge listening at each Bukkit priority
   * uses this so Adapt handlers interleave with other plugins by priority.
   */
  public <E extends AdaptEvent> E post(E event, EventPriority priority) {
    Table table = table(event.getClass());
    int ordinal = priority.ordinal();
    dispatch(table.handlers, table.starts[ordinal], table.starts[ordinal + 1], event);
    return event;
  }

  private void dispatch(Registration[] handlers, int from, int to, AdaptEvent event) {
    Cancellable cancellable = event instanceof Cancellable c ? c : null;
    for (int i = from; i < to; i++) {
      Registration registration = handlers[i];
      if (!registration.active) {
        continue;
      }
      if (cancellable != null && registration.ignoreCancelled && cancellable.isCancelled()) {
        continue;
      }
      try {
        registration.listener.handle(event);
      } catch (VirtualMachineError fatal) {
        throw fatal;
      } catch (Throwable error) {
        reportFailure(registration.owner, event, error);
      }
    }
  }

  private void notifyRegistration(Class<? extends AdaptEvent> type) {
    for (Consumer<Class<? extends AdaptEvent>> listener : registrationListeners) {
      try {
        listener.accept(type);
      } catch (VirtualMachineError fatal) {
        throw fatal;
      } catch (Throwable error) {
        reportFailure(listener, null, error);
      }
    }
  }

  private void reportFailure(Object owner, AdaptEvent event, Throwable error) {
    try {
      errorHandler.handlerFailed(owner, event, error);
    } catch (VirtualMachineError fatal) {
      throw fatal;
    } catch (Throwable ignored) {
      // A failing error handler must not break dispatch.
    }
  }

  private Table table(Class<?> eventClass) {
    Table table = tables.get(eventClass);
    if (table != null) {
      return table;
    }
    synchronized (lock) {
      table = tables.get(eventClass);
      if (table == null) {
        table = bake(eventClass);
        tables.put(eventClass, table);
      }
      return table;
    }
  }

  private Table bake(Class<?> eventClass) {
    List<Registration> matched = new ArrayList<>();
    for (Map.Entry<Class<?>, List<Registration>> entry : byType.entrySet()) {
      if (entry.getKey().isAssignableFrom(eventClass)) {
        matched.addAll(entry.getValue());
      }
    }
    if (matched.isEmpty()) {
      return Table.EMPTY;
    }
    matched.sort(ORDER);
    Registration[] handlers = matched.toArray(NO_HANDLERS);
    int[] starts = new int[PRIORITY_COUNT + 1];
    int mask = 0;
    int cancelledMask = 0;
    int index = 0;
    for (int ordinal = 0; ordinal < PRIORITY_COUNT; ordinal++) {
      starts[ordinal] = index;
      while (index < handlers.length && handlers[index].priority.ordinal() == ordinal) {
        if (!handlers[index].ignoreCancelled) {
          cancelledMask |= EventPriority.at(ordinal).bit();
        }
        index++;
      }
      if (index > starts[ordinal]) {
        mask |= EventPriority.at(ordinal).bit();
      }
    }
    starts[PRIORITY_COUNT] = handlers.length;
    return new Table(handlers, starts, mask, cancelledMask);
  }

  private void removeFromType(Registration registration) {
    List<Registration> typed = byType.get(registration.type);
    if (typed != null) {
      typed.remove(registration);
      if (typed.isEmpty()) {
        byType.remove(registration.type);
      }
    }
  }

  private void unregister(Registration registration) {
    synchronized (lock) {
      if (!registration.active) {
        return;
      }
      registration.active = false;
      removeFromType(registration);
      List<Registration> owned = byOwner.get(registration.owner);
      if (owned != null) {
        owned.remove(registration);
        if (owned.isEmpty()) {
          byOwner.remove(registration.owner);
        }
      }
      tables.clear();
    }
    notifyRegistration(registration.type);
  }

  private record Table(Registration[] handlers, int[] starts, int priorityMask, int receivesCancelledMask) {
    static final Table EMPTY = new Table(NO_HANDLERS, new int[PRIORITY_COUNT + 1], 0, 0);
  }

  private static final class Registration implements Subscription {
    private final AdaptEventBus bus;
    private final Object owner;
    private final Class<? extends AdaptEvent> type;
    private final EventPriority priority;
    private final boolean ignoreCancelled;
    private final EventListener<Object> listener;
    private final long sequence;
    private volatile boolean active = true;

    private Registration(AdaptEventBus bus, Object owner, Class<? extends AdaptEvent> type, EventPriority priority,
                         boolean ignoreCancelled, EventListener<Object> listener, long sequence) {
      this.bus = bus;
      this.owner = owner;
      this.type = type;
      this.priority = priority;
      this.ignoreCancelled = ignoreCancelled;
      this.listener = listener;
      this.sequence = sequence;
    }

    @Override
    public Object owner() {
      return owner;
    }

    @Override
    public Class<? extends AdaptEvent> eventType() {
      return type;
    }

    @Override
    public EventPriority priority() {
      return priority;
    }

    @Override
    public boolean ignoreCancelled() {
      return ignoreCancelled;
    }

    @Override
    public boolean isActive() {
      return active;
    }

    @Override
    public void unregister() {
      bus.unregister(this);
    }
  }
}
