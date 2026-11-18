package art.arcane.adapt.platform.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotatedHandlersTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));

  @Test
  void discoversHandlersOncePerClassWithAnnotationSettings() {
    List<HandlerMethod> methods = AnnotatedHandlers.of(Component.class);

    assertEquals(3, methods.size());
    assertSame(methods, AnnotatedHandlers.of(Component.class));
    HandlerMethod onBase = methods.stream().filter(method -> method.method().getName().equals("onBase")).findFirst().orElseThrow();
    assertEquals(EventPriority.HIGH, onBase.priority());
    assertTrue(onBase.ignoreCancelled());
    HandlerMethod onCancelled = methods.stream().filter(method -> method.method().getName().equals("onCancelled")).findFirst().orElseThrow();
    assertFalse(onCancelled.ignoreCancelled());
    assertEquals(EventPriority.MONITOR, onCancelled.priority());
    assertFalse(AnnotatedHandlers.declaresHandlers(Object.class));
  }

  @Test
  void registersPrivateAndInheritedHandlersAndDispatches() {
    Component component = new Component();

    assertEquals(3, AnnotatedHandlers.register(bus, component));
    bus.post(new TestEvents.Child());
    bus.post(new TestEvents.Plain());

    assertEquals(List.of("base", "cancelled-false", "plain"), component.seen);
    assertTrue(failures.isEmpty());
  }

  @Test
  void overriddenHandlerRegistersOnce() {
    Overriding component = new Overriding();

    assertEquals(3, AnnotatedHandlers.register(bus, component));
    bus.post(new TestEvents.Base());

    assertEquals(List.of("override", "cancelled-false"), component.seen);
  }

  @Test
  void ignoreCancelledDefaultSkipsCancelledEvents() {
    Component component = new Component();
    AnnotatedHandlers.register(bus, component);
    TestEvents.Base event = new TestEvents.Base();
    event.setCancelled(true);

    bus.post(event);

    assertEquals(List.of("cancelled-true"), component.seen);
  }

  @Test
  void unregisterAllRemovesEveryHandlerOfTheOwner() {
    Component component = new Component();
    AnnotatedHandlers.register(bus, component);

    assertEquals(3, bus.unregisterAll(component));
    bus.post(new TestEvents.Base());

    assertTrue(component.seen.isEmpty());
    assertFalse(bus.hasListeners(TestEvents.Base.class));
  }

  @Test
  void decoratorWrapsSkipsAndOverridesCancelledFilter() {
    Component component = new Component();
    List<String> gate = new ArrayList<>();
    AnnotatedHandlers.Decorator decorator = new AnnotatedHandlers.Decorator() {
      @Override
      public EventListener<AdaptEvent> decorate(Object owner, HandlerMethod method, EventListener<AdaptEvent> call) {
        if (method.eventType() == TestEvents.Plain.class) {
          return null;
        }
        return event -> {
          gate.add(method.method().getName());
          call.handle(event);
        };
      }

      @Override
      public boolean ignoreCancelled(Object owner, HandlerMethod method) {
        return false;
      }
    };

    assertEquals(2, AnnotatedHandlers.register(bus, component, decorator));
    TestEvents.Base event = new TestEvents.Base();
    event.setCancelled(true);
    bus.post(event);
    bus.post(new TestEvents.Plain());

    assertEquals(List.of("onBase", "onCancelled"), gate);
    assertEquals(List.of("base", "cancelled-true"), component.seen);
  }

  @Test
  void handlerFailureReachesErrorHandler() {
    Throwing component = new Throwing();
    AnnotatedHandlers.register(bus, component);

    bus.post(new TestEvents.Plain());

    assertEquals(1, failures.size());
    assertEquals("boom", failures.getFirst().getMessage());
  }

  @Test
  void rejectsMalformedHandlers() {
    assertThrows(IllegalArgumentException.class, () -> AnnotatedHandlers.of(WrongParameter.class));
    assertThrows(IllegalArgumentException.class, () -> AnnotatedHandlers.of(StaticHandler.class));
  }

  @Test
  void registrationListenerSeesRegisterAndUnregister() {
    List<Class<? extends AdaptEvent>> changes = new ArrayList<>();
    bus.addRegistrationListener(changes::add);
    Component component = new Component();

    AnnotatedHandlers.register(bus, component);
    bus.unregisterAll(component);
    bus.clear();

    assertEquals(List.of(TestEvents.Base.class, TestEvents.Base.class, TestEvents.Plain.class,
        TestEvents.Base.class, TestEvents.Plain.class, AdaptEvent.class), changes);
  }

  @Test
  void receivesCancelledMaskTracksHandlersThatSeeCancelledEvents() {
    Object owner = new Object();
    bus.register(owner, TestEvents.Base.class, EventPriority.LOW, true, event -> {
    });
    assertEquals(0, bus.receivesCancelledMask(TestEvents.Base.class));

    bus.register(owner, TestEvents.Base.class, EventPriority.HIGH, false, event -> {
    });
    assertEquals(EventPriority.HIGH.bit(), bus.receivesCancelledMask(TestEvents.Child.class));
  }

  static class Component {
    final List<String> seen = new ArrayList<>();

    @Handle(priority = EventPriority.HIGH)
    void onBase(TestEvents.Base event) {
      seen.add("base");
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    private void onCancelled(TestEvents.Base event) {
      seen.add("cancelled-" + event.isCancelled());
    }

    @Handle
    public void onPlain(TestEvents.Plain event) {
      seen.add("plain");
    }
  }

  static class Overriding extends Component {
    @Override
    @Handle(priority = EventPriority.LOW)
    void onBase(TestEvents.Base event) {
      seen.add("override");
    }
  }

  static class Throwing {
    @Handle
    void onPlain(TestEvents.Plain event) {
      throw new IllegalStateException("boom");
    }
  }

  static class WrongParameter {
    @Handle
    void onString(String value) {
    }
  }

  static class StaticHandler {
    @Handle
    static void onPlain(TestEvents.Plain event) {
    }
  }
}
