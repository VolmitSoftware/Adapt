package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * One {@link Handle} method of a class, with a prebuilt invoker.
 *
 * @param method the annotated method
 * @param eventType the event class the method takes
 * @param priority the annotation's priority
 * @param ignoreCancelled the annotation's cancelled filter
 * @param invoker calls the method on a target object without reflection
 */
@ApiStatus.AvailableSince("1.0.0")
public record HandlerMethod(Method method, Class<? extends AdaptEvent> eventType, EventPriority priority,
                            boolean ignoreCancelled, Invoker invoker) {
  public HandlerMethod {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(invoker, "invoker");
  }

  /** True when the method also carries the given annotation, so a decorator can read marker annotations. */
  public boolean isAnnotationPresent(Class<? extends Annotation> annotation) {
    return method.isAnnotationPresent(annotation);
  }

  /** Readable name for logs, such as {@code com.example.Foo#onBreak(BlockBreakEvent)}. */
  public String describe() {
    return method.getDeclaringClass().getName() + "#" + method.getName() + "(" + eventType.getSimpleName() + ")";
  }

  /** Calls a handler method on its owner object. */
  @FunctionalInterface
  public interface Invoker {
    /** Calls the handler on {@code target} with {@code event}; whatever the handler throws passes through. */
    void invoke(Object target, AdaptEvent event) throws Exception;
  }
}
