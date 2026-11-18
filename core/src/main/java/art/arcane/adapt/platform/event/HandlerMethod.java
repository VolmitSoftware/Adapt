package art.arcane.adapt.platform.event;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;

/** One {@link Handle} method of a class, with a prebuilt invoker. */
public record HandlerMethod(Method method, Class<? extends AdaptEvent> eventType, EventPriority priority,
                            boolean ignoreCancelled, Invoker invoker) {
  public HandlerMethod {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(invoker, "invoker");
  }

  public boolean isAnnotationPresent(Class<? extends Annotation> annotation) {
    return method.isAnnotationPresent(annotation);
  }

  public String describe() {
    return method.getDeclaringClass().getName() + "#" + method.getName() + "(" + eventType.getSimpleName() + ")";
  }

  @FunctionalInterface
  public interface Invoker {
    void invoke(Object target, AdaptEvent event) throws Exception;
  }
}
