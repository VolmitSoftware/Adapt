package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Subscription;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class BukkitEventBridgeTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private final AtomicInteger translated = new AtomicInteger();
  private final List<String> order = new ArrayList<>();
  private Plugin adapt;
  private Plugin other;
  private BukkitEventBridge bridge;

  @BeforeEach
  void setUp() {
    adapt = plugin("Adapt");
    other = plugin("Other");
    bridge = new BukkitEventBridge(adapt, bus, List.of(new TestTranslation()));
    bridge.start();
  }

  @AfterEach
  void tearDown() {
    bridge.stop();
    HandlerList.unregisterAll(adapt);
    HandlerList.unregisterAll(other);
    assertThat(failures).isEmpty();
  }

  @Test
  void listensOnlyAtPrioritiesWithBusHandlers() {
    assertThat(bridge.listeningMask(NativeEvent.class)).isZero();
    assertThat(adaptListeners()).isZero();

    Subscription low = bus.register(this, CoreEvent.class, EventPriority.LOW, event -> {
    });
    Subscription monitor = bus.register(this, CoreEvent.class, EventPriority.MONITOR, event -> {
    });
    assertThat(bridge.listeningMask(NativeEvent.class)).isEqualTo(EventPriority.LOW.bit() | EventPriority.MONITOR.bit());
    assertThat(adaptListeners()).isEqualTo(2);

    low.unregister();
    assertThat(bridge.listeningMask(NativeEvent.class)).isEqualTo(EventPriority.MONITOR.bit());
    monitor.unregister();
    assertThat(bridge.listeningMask(NativeEvent.class)).isZero();
    assertThat(adaptListeners()).isZero();
  }

  @Test
  void supertypeRegistrationEnablesTheTranslation() {
    bus.register(this, CancellableEvent.class, EventPriority.HIGH, event -> {
    });

    assertThat(bridge.listeningMask(NativeEvent.class)).isEqualTo(EventPriority.HIGH.bit());
    bus.unregisterAll(this);
    assertThat(bridge.listeningMask(NativeEvent.class)).isZero();
  }

  @Test
  void stopRemovesEveryNativeListener() {
    bus.register(this, CoreEvent.class, EventPriority.NORMAL, event -> {
    });
    bridge.stop();

    assertThat(adaptListeners()).isZero();
    bus.register(this, CoreEvent.class, EventPriority.HIGH, event -> {
    });
    assertThat(adaptListeners()).isZero();
  }

  @Test
  void protectionProbesOfOtherEventsStillReachTheBus() {
    bus.register(this, CoreEvent.class, EventPriority.NORMAL, event -> order.add("adapt"));
    PluginManager plugins = mock(PluginManager.class);
    doAnswer(invocation -> fire(invocation.getArgument(0))).when(plugins).callEvent(any(Event.class));
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
      ProtectionEventProbe.dispatch(new NativeEvent());
    }
    assertThat(order).containsExactly("adapt");

    fire(new NativeEvent());

    assertThat(order).containsExactly("adapt", "adapt");
  }

  @Test
  void busHandlersInterleaveWithOtherPluginsByPriority() {
    bus.register(this, CoreEvent.class, EventPriority.LOW, event -> order.add("adapt-low"));
    bus.register(this, CoreEvent.class, EventPriority.HIGH, event -> order.add("adapt-high"));
    bus.register(this, CoreEvent.class, EventPriority.MONITOR, event -> order.add("adapt-monitor"));
    foreign(org.bukkit.event.EventPriority.LOWEST, event -> order.add("other-lowest"));
    foreign(org.bukkit.event.EventPriority.NORMAL, event -> order.add("other-normal"));
    foreign(org.bukkit.event.EventPriority.HIGHEST, event -> order.add("other-highest"));

    fire(new NativeEvent());

    assertThat(order).containsExactly("other-lowest", "adapt-low", "other-normal", "adapt-high", "other-highest",
        "adapt-monitor");
  }

  @Test
  void cancellationFlowsBothWaysBetweenPlugins() {
    bus.register(this, CoreEvent.class, EventPriority.LOW, event -> event.setCancelled(true));
    foreign(org.bukkit.event.EventPriority.NORMAL, event -> {
      order.add("other-sees-" + event.isCancelled());
      event.setCancelled(false);
      event.value = 5;
    });
    bus.register(this, CoreEvent.class, EventPriority.HIGH, event -> {
      order.add("adapt-high-" + event.value);
      event.value = 9;
    });

    NativeEvent event = fire(new NativeEvent());

    assertThat(order).containsExactly("other-sees-true", "adapt-high-5");
    assertThat(event.value).isEqualTo(9);
    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void everyPriorityOfOneNativeEventSharesADispatchIdAndSeesItsLaterCancel() {
    List<CoreEvent> seen = new ArrayList<>();
    bus.register(this, CoreEvent.class, EventPriority.LOW, seen::add);
    bus.register(this, CoreEvent.class, EventPriority.MONITOR, false, seen::add);
    foreign(org.bukkit.event.EventPriority.MONITOR, event -> event.setCancelled(true));

    fire(new NativeEvent());
    fire(new NativeEvent());

    assertThat(seen).hasSize(4);
    assertThat(seen.get(0)).isNotSameAs(seen.get(1));
    assertThat(seen.get(0).dispatchId()).isEqualTo(seen.get(1).dispatchId());
    assertThat(seen.get(2).dispatchId()).isEqualTo(seen.get(3).dispatchId()).isNotEqualTo(seen.get(0).dispatchId());
    assertThat(seen.get(1).isCancelled()).isFalse();
    assertThat(seen.get(1).dispatchCancelled()).isTrue();
  }

  @Test
  void cancelledEventIsNotTranslatedWhenNoHandlerWantsIt() {
    bus.register(this, CoreEvent.class, EventPriority.HIGH, event -> order.add("ignored"));
    NativeEvent event = new NativeEvent();
    event.setCancelled(true);

    fire(event);

    assertThat(translated).hasValue(0);
    bus.register(this, CoreEvent.class, EventPriority.HIGH, false, event2 -> order.add("receives"));
    fire(event);
    assertThat(translated).hasValue(1);
    assertThat(order).containsExactly("receives");
  }

  @Test
  void priorityWithoutHandlerForThisEventClassSkipsTranslation() {
    bus.register(this, OtherCoreEvent.class, EventPriority.NORMAL, event -> {
    });
    bus.register(this, CoreEvent.class, EventPriority.HIGH, event -> {
    });

    fire(new NativeEvent());

    assertThat(translated).hasValue(1);
  }

  private static Plugin plugin(String name) {
    Plugin plugin = mock(Plugin.class);
    lenient().when(plugin.isEnabled()).thenReturn(true);
    lenient().when(plugin.getName()).thenReturn(name);
    return plugin;
  }

  private long adaptListeners() {
    long count = 0;
    for (RegisteredListener listener : NativeEvent.HANDLERS.getRegisteredListeners()) {
      if (listener.getPlugin() == adapt) {
        count++;
      }
    }
    return count;
  }

  private void foreign(org.bukkit.event.EventPriority priority, java.util.function.Consumer<NativeEvent> action) {
    Listener listener = new Listener() {
    };
    NativeEvent.HANDLERS.register(new RegisteredListener(listener, (ignored, event) -> action.accept((NativeEvent) event),
        priority, other, false));
  }

  private static NativeEvent fire(NativeEvent event) {
    for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
      try {
        listener.callEvent(event);
      } catch (Exception failure) {
        throw new IllegalStateException(failure);
      }
    }
    return event;
  }

  public static final class NativeEvent extends Event implements Cancellable {
    static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    int value;

    public static HandlerList getHandlerList() {
      return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
      return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
    }
  }

  static class CoreEvent extends CancellableEvent {
    int value;
  }

  static final class OtherCoreEvent extends CancellableEvent {
  }

  private final class TestTranslation extends Translation<NativeEvent> {
    private TestTranslation() {
      super(NativeEvent.class, List.of(CoreEvent.class));
    }

    @Override
    public void bridge(NativeEvent event, Poster poster) {
      translated.incrementAndGet();
      boolean cancelled = event.isCancelled();
      CoreEvent core = new CoreEvent();
      core.value = event.value;
      core.setCancelled(cancelled);
      poster.post(core);
      BukkitTranslations.cancel(event, cancelled, core);
      event.value = core.value;
    }
  }
}
