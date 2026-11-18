package art.arcane.adapt.platform.event;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registers the {@link Handle} methods of an object on an {@link AdaptEventBus}, with the object as
 * owner. Methods are found once per class (superclasses included, overrides win) and called through
 * generated lambdas, so dispatch does no reflection. Remove them with
 * {@link AdaptEventBus#unregisterAll(Object)}.
 */
public final class AnnotatedHandlers {
  private static final Comparator<Method> METHOD_ORDER = Comparator.comparing(Method::getName)
      .thenComparing(method -> method.getParameterTypes()[0].getName());
  private static final ClassValue<List<HandlerMethod>> CACHE = new ClassValue<>() {
    @Override
    protected List<HandlerMethod> computeValue(Class<?> type) {
      return scan(type);
    }
  };

  private AnnotatedHandlers() {
  }

  /** Handler methods of a class. Throws when a {@link Handle} method has the wrong shape. */
  public static List<HandlerMethod> of(Class<?> type) {
    return CACHE.get(type);
  }

  public static boolean declaresHandlers(Class<?> type) {
    return !of(type).isEmpty();
  }

  public static int register(AdaptEventBus bus, Object owner) {
    return register(bus, owner, Decorator.NONE);
  }

  /** Registers every handler of the owner's class. Returns how many were registered. */
  public static int register(AdaptEventBus bus, Object owner, Decorator decorator) {
    Objects.requireNonNull(bus, "bus");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(decorator, "decorator");
    int registered = 0;
    for (HandlerMethod method : of(owner.getClass())) {
      HandlerMethod.Invoker invoker = method.invoker();
      EventListener<AdaptEvent> call = event -> invoker.invoke(owner, event);
      EventListener<AdaptEvent> listener = decorator.decorate(owner, method, call);
      if (listener == null) {
        continue;
      }
      registerOne(bus, owner, method.eventType(), method.priority(), decorator.ignoreCancelled(owner, method), listener);
      registered++;
    }
    return registered;
  }

  private static <E extends AdaptEvent> void registerOne(AdaptEventBus bus, Object owner, Class<E> type,
                                                         EventPriority priority, boolean ignoreCancelled,
                                                         EventListener<AdaptEvent> listener) {
    bus.register(owner, type, priority, ignoreCancelled, listener);
  }

  private static List<HandlerMethod> scan(Class<?> type) {
    Map<String, Method> found = new LinkedHashMap<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Handle.class) && !method.isBridge() && !method.isSynthetic()) {
          found.putIfAbsent(method.getName() + "|" + Arrays.toString(method.getParameterTypes()), method);
        }
      }
      current = current.getSuperclass();
    }
    if (found.isEmpty()) {
      return List.of();
    }
    List<Method> methods = new ArrayList<>(found.values());
    for (Method method : methods) {
      validate(method);
    }
    methods.sort(METHOD_ORDER);
    List<HandlerMethod> handlers = new ArrayList<>(methods.size());
    for (Method method : methods) {
      Handle handle = method.getAnnotation(Handle.class);
      Class<? extends AdaptEvent> eventType = method.getParameterTypes()[0].asSubclass(AdaptEvent.class);
      handlers.add(new HandlerMethod(method, eventType, handle.priority(), handle.ignoreCancelled(), invoker(method)));
    }
    return List.copyOf(handlers);
  }

  private static void validate(Method method) {
    if (Modifier.isStatic(method.getModifiers())) {
      throw new IllegalArgumentException("@Handle method must not be static: " + method);
    }
    if (method.getParameterCount() != 1 || !AdaptEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
      throw new IllegalArgumentException("@Handle method must take exactly one AdaptEvent: " + method);
    }
  }

  private static HandlerMethod.Invoker invoker(Method method) {
    MethodHandles.Lookup lookup = lookupFor(method.getDeclaringClass());
    MethodHandle handle;
    try {
      handle = lookup.unreflect(method);
    } catch (IllegalAccessException denied) {
      method.setAccessible(true);
      try {
        handle = MethodHandles.lookup().unreflect(method);
      } catch (IllegalAccessException stillDenied) {
        throw new IllegalStateException("Cannot access handler " + method, stillDenied);
      }
    }
    HandlerMethod.Invoker generated = generated(lookup, method, handle);
    if (generated != null) {
      return generated;
    }
    MethodHandle erased = handle.asType(MethodType.methodType(void.class, Object.class, AdaptEvent.class));
    return (target, event) -> {
      try {
        erased.invokeExact(target, event);
      } catch (Exception | Error thrown) {
        throw thrown;
      } catch (Throwable other) {
        throw new IllegalStateException(other);
      }
    };
  }

  private static MethodHandles.Lookup lookupFor(Class<?> type) {
    try {
      return MethodHandles.privateLookupIn(type, MethodHandles.lookup());
    } catch (IllegalAccessException | RuntimeException denied) {
      return MethodHandles.lookup();
    }
  }

  private static HandlerMethod.Invoker generated(MethodHandles.Lookup lookup, Method method, MethodHandle handle) {
    try {
      MethodType sam = MethodType.methodType(void.class, Object.class, AdaptEvent.class);
      MethodType instantiated = MethodType.methodType(void.class, method.getDeclaringClass(), method.getParameterTypes()[0]);
      return (HandlerMethod.Invoker) LambdaMetafactory.metafactory(lookup, "invoke",
          MethodType.methodType(HandlerMethod.Invoker.class), sam, handle, instantiated).getTarget().invokeExact();
    } catch (Throwable unsupported) {
      return null;
    }
  }

  /** Lets a platform wrap or skip each handler, and adjust its cancelled filter, at registration. */
  public interface Decorator {
    Decorator NONE = (owner, method, call) -> call;

    /** The listener to register for this method, or null to leave the method unregistered. */
    EventListener<AdaptEvent> decorate(Object owner, HandlerMethod method, EventListener<AdaptEvent> call);

    default boolean ignoreCancelled(Object owner, HandlerMethod method) {
      return method.ignoreCancelled();
    }
  }
}
