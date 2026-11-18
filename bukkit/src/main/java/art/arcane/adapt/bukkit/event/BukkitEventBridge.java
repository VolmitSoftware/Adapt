package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Feeds Bukkit events into the {@link AdaptEventBus}.
 *
 * <p>For each translation the bridge keeps one Bukkit listener per priority that has bus handlers,
 * registered in the native handler list with Adapt as the owning plugin, so bus handlers interleave
 * with other plugins exactly as Bukkit handlers did. Each listener posts only its own priority. The
 * listener set follows bus registrations through {@link AdaptEventBus#addRegistrationListener}.
 *
 * <p>Protection probes are posted marked as probes ({@link AdaptEvent#protectionProbe()}); skills and adaptations skip
 * them, as before. Dispatch is lock-free and allocates nothing until a handler will actually run; listener changes are
 * serialised.
 */
public final class BukkitEventBridge {
  private static final EventPriority[] PRIORITIES = EventPriority.values();
  private static final org.bukkit.event.EventPriority[] NATIVE_PRIORITIES = org.bukkit.event.EventPriority.values();
  private static volatile BukkitEventBridge installed;

  private final Plugin plugin;
  private final AdaptEventBus bus;
  private final List<Entry> entries;
  private final Consumer<Class<? extends AdaptEvent>> onChange = this::refresh;
  private final Object lock = new Object();
  private boolean running;

  BukkitEventBridge(Plugin plugin, AdaptEventBus bus, List<Translation<?>> translations) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.bus = Objects.requireNonNull(bus, "bus");
    List<Entry> built = new ArrayList<>(translations.size());
    for (Translation<?> translation : translations) {
      built.add(new Entry(translation, handlerList(translation.nativeType())));
    }
    this.entries = List.copyOf(built);
  }

  /** Starts the bridge for the plugin's bus with every translation. Replaces a previous installation. */
  private static final ThreadLocal<Event> BRIDGING = new ThreadLocal<>();

  /** The native event whose core translation is being posted on this thread, or null. */
  public static Event bridging() {
    return BRIDGING.get();
  }

  public static synchronized BukkitEventBridge install(Plugin plugin, AdaptEventBus bus) {
    uninstall();
    BukkitEventBridge bridge = new BukkitEventBridge(plugin, bus, BukkitTranslations.all());
    bridge.start();
    installed = bridge;
    return bridge;
  }

  public static synchronized void uninstall() {
    BukkitEventBridge previous = installed;
    installed = null;
    if (previous != null) {
      previous.stop();
    }
  }

  public AdaptEventBus bus() {
    return bus;
  }

  void start() {
    synchronized (lock) {
      running = true;
    }
    bus.addRegistrationListener(onChange);
    refresh(AdaptEvent.class);
  }

  void stop() {
    bus.removeRegistrationListener(onChange);
    synchronized (lock) {
      running = false;
      for (Entry entry : entries) {
        entry.apply(0);
      }
    }
  }

  /** Bit set of bus priorities with a live Bukkit listener for this native event class. */
  int listeningMask(Class<? extends Event> nativeType) {
    synchronized (lock) {
      for (Entry entry : entries) {
        if (entry.translation.nativeType() == nativeType) {
          return entry.mask;
        }
      }
      return 0;
    }
  }

  void refresh(Class<? extends AdaptEvent> changed) {
    synchronized (lock) {
      if (!running) {
        return;
      }
      for (Entry entry : entries) {
        if (entry.affectedBy(changed)) {
          entry.apply(entry.desiredMask());
        }
      }
    }
  }

  private static HandlerList handlerList(Class<?> nativeType) {
    Class<?> current = nativeType;
    while (current != null && Event.class.isAssignableFrom(current)) {
      try {
        Method method = current.getDeclaredMethod("getHandlerList");
        method.setAccessible(true);
        return (HandlerList) method.invoke(null);
      } catch (NoSuchMethodException missing) {
        current = current.getSuperclass();
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Cannot read the handler list of " + nativeType.getName(), failure);
      }
    }
    throw new IllegalStateException(nativeType.getName() + " has no handler list");
  }

  private final class Entry {
    private final Translation<?> translation;
    private final HandlerList handlers;
    private final RegisteredListener[] registered = new RegisteredListener[PRIORITIES.length];
    private int mask;

    private Entry(Translation<?> translation, HandlerList handlers) {
      this.translation = translation;
      this.handlers = handlers;
    }

    private boolean affectedBy(Class<? extends AdaptEvent> changed) {
      for (Class<? extends AdaptEvent> type : translation.posted()) {
        if (changed.isAssignableFrom(type)) {
          return true;
        }
      }
      return false;
    }

    private int desiredMask() {
      int desired = 0;
      for (Class<? extends AdaptEvent> type : translation.posted()) {
        desired |= bus.priorityMask(type);
      }
      return desired;
    }

    private void apply(int desired) {
      if (desired == mask) {
        return;
      }
      for (EventPriority priority : PRIORITIES) {
        int bit = priority.bit();
        if ((desired & bit) != 0 && (mask & bit) == 0) {
          Slot<?> slot = new Slot<>(translation, priority);
          RegisteredListener listener = new RegisteredListener(slot, slot, NATIVE_PRIORITIES[priority.ordinal()], plugin, false);
          handlers.register(listener);
          registered[priority.ordinal()] = listener;
        } else if ((desired & bit) == 0 && (mask & bit) != 0) {
          handlers.unregister(registered[priority.ordinal()]);
          registered[priority.ordinal()] = null;
        }
      }
      mask = desired;
    }
  }

  private final class Slot<B extends Event> implements Listener, org.bukkit.plugin.EventExecutor, Translation.Poster {
    private final Translation<B> translation;
    private final EventPriority priority;
    private final int bit;

    private Slot(Translation<B> translation, EventPriority priority) {
      this.translation = translation;
      this.priority = priority;
      this.bit = priority.bit();
    }

    @Override
    public void execute(Listener listener, Event raw) {
      if (!translation.nativeType().isInstance(raw)) {
        return;
      }
      B event = translation.nativeType().cast(raw);
      Class<? extends AdaptEvent> target = translation.target(event);
      if (target == null || (bus.priorityMask(target) & bit) == 0) {
        return;
      }
      if (translation.cancelled(event) && (bus.receivesCancelledMask(target) & bit) == 0) {
        return;
      }
      Event previous = BRIDGING.get();
      BRIDGING.set(raw);
      try {
        translation.bridge(event, this);
      } finally {
        if (previous == null) {
          BRIDGING.remove();
        } else {
          BRIDGING.set(previous);
        }
      }
    }

    @Override
    public void post(AdaptEvent event) {
      Event raw = BRIDGING.get();
      if (raw != null) {
        event.setDispatchId(NativeDispatchIds.of(raw));
        if (raw instanceof org.bukkit.event.Cancellable cancellable) {
          event.setDispatchCancelled(cancellable::isCancelled);
        }
        if (isProtectionProbe(raw)) {
          event.markProtectionProbe();
        }
      }
      bus.post(event, priority);
    }
  }

  private static boolean isProtectionProbe(Event raw) {
    return ProtectionEventProbe.isActive(raw)
        && (raw instanceof PlayerInteractEvent
        || raw instanceof PlayerInteractEntityEvent
        || raw instanceof BlockBreakEvent
        || raw instanceof BlockPlaceEvent);
  }
}
