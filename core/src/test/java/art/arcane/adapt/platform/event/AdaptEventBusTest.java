package art.arcane.adapt.platform.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptEventBusTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));

  @Test
  void runsByPriorityThenRegistrationOrder() {
    List<String> order = new ArrayList<>();
    Object owner = new Object();
    bus.register(owner, TestEvents.Base.class, EventPriority.MONITOR, false, event -> order.add("monitor"));
    bus.register(owner, TestEvents.Base.class, EventPriority.HIGHEST, false, event -> order.add("highest-1"));
    bus.register(owner, TestEvents.Base.class, EventPriority.LOWEST, false, event -> order.add("lowest"));
    bus.register(owner, TestEvents.Base.class, EventPriority.HIGHEST, false, event -> order.add("highest-2"));
    bus.register(owner, TestEvents.Base.class, EventPriority.NORMAL, false, event -> order.add("normal"));

    bus.post(new TestEvents.Base());

    assertEquals(List.of("lowest", "normal", "highest-1", "highest-2", "monitor"), order);
  }

  @Test
  void supertypeHandlersReceiveSubtypesInMergedOrder() {
    List<String> order = new ArrayList<>();
    Object owner = new Object();
    bus.register(owner, TestEvents.Child.class, EventPriority.HIGH, false, event -> order.add("child-high"));
    bus.register(owner, TestEvents.Base.class, EventPriority.HIGH, false, event -> order.add("base-high"));
    bus.register(owner, TestEvents.Base.class, EventPriority.LOW, false, event -> order.add("base-low"));
    bus.register(owner, AdaptEvent.class, EventPriority.MONITOR, false, event -> order.add("any"));

    bus.post(new TestEvents.Child());
    assertEquals(List.of("base-low", "child-high", "base-high", "any"), order);

    order.clear();
    bus.post(new TestEvents.Base());
    assertEquals(List.of("base-low", "base-high", "any"), order);
  }

  @Test
  void ignoreCancelledSkipsOnlyAfterCancellation() {
    List<String> seen = new ArrayList<>();
    Object owner = new Object();
    bus.register(owner, TestEvents.Base.class, EventPriority.LOW, true, event -> seen.add("low"));
    bus.register(owner, TestEvents.Base.class, EventPriority.NORMAL, false, event -> event.setCancelled(true));
    bus.register(owner, TestEvents.Base.class, EventPriority.HIGH, true, event -> seen.add("high-ignored"));
    bus.register(owner, TestEvents.Base.class, EventPriority.MONITOR, false, event -> seen.add("monitor-" + event.isCancelled()));

    TestEvents.Base event = bus.post(new TestEvents.Base());

    assertTrue(event.isCancelled());
    assertEquals(List.of("low", "monitor-true"), seen);
  }

  @Test
  void uncancellingLetsLaterHandlersRun() {
    List<String> seen = new ArrayList<>();
    Object owner = new Object();
    bus.register(owner, TestEvents.Base.class, EventPriority.LOW, false, event -> event.setCancelled(false));
    bus.register(owner, TestEvents.Base.class, EventPriority.HIGH, true, event -> seen.add("ran"));
    TestEvents.Base event = new TestEvents.Base();
    event.setCancelled(true);

    bus.post(event);

    assertEquals(List.of("ran"), seen);
  }

  @Test
  void defaultRegistrationIgnoresCancelled() {
    List<String> seen = new ArrayList<>();
    bus.register(this, TestEvents.Base.class, event -> seen.add("ran"));
    TestEvents.Base event = new TestEvents.Base();
    event.setCancelled(true);
    bus.post(event);
    assertTrue(seen.isEmpty());
  }

  @Test
  void mutationsAreVisibleToLaterHandlers() {
    Object owner = new Object();
    bus.register(owner, TestEvents.Base.class, EventPriority.LOW, event -> event.value = event.value + 2);
    bus.register(owner, TestEvents.Base.class, EventPriority.HIGH, event -> event.value = event.value * 10);
    TestEvents.Base event = new TestEvents.Base();
    event.value = 1;
    assertEquals(30, bus.post(event).value);
  }

  @Test
  void unregisterAllRemovesOnlyThatOwner() {
    Object first = new Object();
    Object second = new Object();
    TestEvents.Plain event = new TestEvents.Plain();
    bus.register(first, TestEvents.Plain.class, e -> e.hits++);
    bus.register(first, TestEvents.Base.class, e -> e.value++);
    bus.register(second, TestEvents.Plain.class, e -> e.hits += 10);

    assertEquals(2, bus.unregisterAll(first));
    assertEquals(0, bus.unregisterAll(first));
    bus.post(event);

    assertEquals(10, event.hits);
    assertFalse(bus.hasListeners(TestEvents.Base.class));
    assertTrue(bus.subscriptions(first).isEmpty());
    assertEquals(1, bus.subscriptions(second).size());
  }

  @Test
  void ownersUseIdentityNotEquals() {
    String first = new String("adaptation");
    String second = new String("adaptation");
    TestEvents.Plain event = new TestEvents.Plain();
    bus.register(first, TestEvents.Plain.class, e -> e.hits++);
    bus.register(second, TestEvents.Plain.class, e -> e.hits += 10);

    bus.unregisterAll(first);
    bus.post(event);

    assertEquals(10, event.hits);
  }

  @Test
  void subscriptionUnregisterIsIdempotent() {
    TestEvents.Plain event = new TestEvents.Plain();
    Subscription subscription = bus.register(this, TestEvents.Plain.class, e -> e.hits++);
    subscription.unregister();
    subscription.unregister();
    bus.post(event);
    assertEquals(0, event.hits);
    assertFalse(subscription.isActive());
  }

  @Test
  void handlerRemovedMidDispatchDoesNotRun() {
    Object owner = new Object();
    TestEvents.Plain event = new TestEvents.Plain();
    Subscription[] later = new Subscription[1];
    bus.register(owner, TestEvents.Plain.class, EventPriority.LOW, false, e -> later[0].unregister());
    later[0] = bus.register(owner, TestEvents.Plain.class, EventPriority.HIGH, false, e -> e.hits++);

    bus.post(event);

    assertEquals(0, event.hits);
  }

  @Test
  void handlerAddedMidDispatchRunsFromNextPost() {
    Object owner = new Object();
    TestEvents.Plain event = new TestEvents.Plain();
    bus.register(owner, TestEvents.Plain.class, EventPriority.LOW, false,
        e -> bus.register(owner, TestEvents.Plain.class, EventPriority.HIGH, false, inner -> inner.hits += 100));

    bus.post(event);
    assertEquals(0, event.hits);
    bus.unregisterAll(owner);
    bus.register(owner, TestEvents.Plain.class, EventPriority.HIGH, false, inner -> inner.hits += 100);
    bus.post(event);
    assertEquals(100, event.hits);
  }

  @Test
  void failingHandlerIsIsolated() {
    List<String> seen = new ArrayList<>();
    Object owner = new Object();
    RuntimeException boom = new IllegalStateException("boom");
    bus.register(owner, TestEvents.Plain.class, EventPriority.LOW, false, e -> {
      throw boom;
    });
    bus.register(owner, TestEvents.Plain.class, EventPriority.NORMAL, false, e -> {
      throw new Exception("checked");
    });
    bus.register(owner, TestEvents.Plain.class, EventPriority.HIGH, false, e -> seen.add("after"));

    bus.post(new TestEvents.Plain());

    assertEquals(List.of("after"), seen);
    assertEquals(2, failures.size());
    assertSame(boom, failures.get(0));
  }

  @Test
  void failingErrorHandlerDoesNotStopDispatch() {
    AdaptEventBus strict = new AdaptEventBus((owner, event, error) -> {
      throw new IllegalStateException("handler broke");
    });
    TestEvents.Plain event = new TestEvents.Plain();
    strict.register(this, TestEvents.Plain.class, EventPriority.LOW, false, e -> {
      throw new IllegalStateException("first");
    });
    strict.register(this, TestEvents.Plain.class, EventPriority.HIGH, false, e -> e.hits++);

    strict.post(event);

    assertEquals(1, event.hits);
  }

  @Test
  void virtualMachineErrorsPropagate() {
    bus.register(this, TestEvents.Plain.class, e -> {
      throw new OutOfMemoryError("test");
    });
    assertThrows(OutOfMemoryError.class, () -> bus.post(new TestEvents.Plain()));
  }

  @Test
  void prioritySlicesDispatchIndependently() {
    List<String> seen = new ArrayList<>();
    Object owner = new Object();
    bus.register(owner, TestEvents.Base.class, EventPriority.LOWEST, false, e -> seen.add("lowest"));
    bus.register(owner, TestEvents.Child.class, EventPriority.HIGHEST, false, e -> seen.add("highest"));
    bus.register(owner, TestEvents.Base.class, EventPriority.MONITOR, false, e -> seen.add("monitor"));

    TestEvents.Child event = new TestEvents.Child();
    for (EventPriority priority : EventPriority.values()) {
      bus.post(event, priority);
    }

    assertEquals(List.of("lowest", "highest", "monitor"), seen);
    assertEquals(EventPriority.LOWEST.bit() | EventPriority.HIGHEST.bit() | EventPriority.MONITOR.bit(),
        bus.priorityMask(TestEvents.Child.class));
    assertEquals(EventPriority.LOWEST.bit() | EventPriority.MONITOR.bit(), bus.priorityMask(TestEvents.Base.class));
    assertTrue(bus.hasListeners(TestEvents.Child.class, EventPriority.HIGHEST));
    assertFalse(bus.hasListeners(TestEvents.Base.class, EventPriority.HIGHEST));
  }

  @Test
  void prioritySliceHonoursIgnoreCancelled() {
    List<String> seen = new ArrayList<>();
    bus.register(this, TestEvents.Base.class, EventPriority.HIGH, true, e -> seen.add("skipped"));
    bus.register(this, TestEvents.Base.class, EventPriority.HIGH, false, e -> seen.add("ran"));
    TestEvents.Base event = new TestEvents.Base();
    event.setCancelled(true);

    bus.post(event, EventPriority.HIGH);

    assertEquals(List.of("ran"), seen);
  }

  @Test
  void hasListenersTracksRegistrations() {
    assertFalse(bus.hasListeners(TestEvents.Child.class));
    Subscription subscription = bus.register(this, TestEvents.Base.class, e -> { });
    assertTrue(bus.hasListeners(TestEvents.Child.class));
    subscription.unregister();
    assertFalse(bus.hasListeners(TestEvents.Child.class));
    assertEquals(0, bus.priorityMask(TestEvents.Child.class));
  }

  @Test
  void clearRemovesEverything() {
    TestEvents.Plain event = new TestEvents.Plain();
    Subscription subscription = bus.register(this, TestEvents.Plain.class, e -> e.hits++);
    bus.clear();
    bus.post(event);
    assertEquals(0, event.hits);
    assertFalse(subscription.isActive());
  }

  @Test
  void concurrentPostsAndRegistrationsStayConsistent() throws InterruptedException {
    TestEvents.Plain[] counters = new TestEvents.Plain[4];
    Thread[] threads = new Thread[4];
    bus.register(this, TestEvents.Plain.class, e -> e.hits++);
    for (int t = 0; t < threads.length; t++) {
      TestEvents.Plain counter = new TestEvents.Plain();
      counters[t] = counter;
      threads[t] = new Thread(() -> {
        for (int i = 0; i < 5000; i++) {
          bus.post(counter);
        }
      });
    }
    Thread churn = new Thread(() -> {
      for (int i = 0; i < 500; i++) {
        Object owner = new Object();
        bus.register(owner, TestEvents.Plain.class, EventPriority.MONITOR, false, e -> { });
        bus.unregisterAll(owner);
      }
    });
    for (Thread thread : threads) {
      thread.start();
    }
    churn.start();
    for (Thread thread : threads) {
      thread.join();
    }
    churn.join();
    for (TestEvents.Plain counter : counters) {
      assertEquals(5000, counter.hits);
    }
    assertTrue(failures.isEmpty());
  }
}
