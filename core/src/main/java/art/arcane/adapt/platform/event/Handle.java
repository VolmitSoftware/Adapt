package art.arcane.adapt.platform.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an instance method taking exactly one {@link AdaptEvent} as a bus handler. Registered through
 * {@link AnnotatedHandlers#register}; discovered once per class.
 *
 * <p>{@code ignoreCancelled} defaults to true, matching the default Adapt forces on cancellable
 * events. Set it to false only for handlers that must see cancelled events.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Handle {
  EventPriority priority() default EventPriority.NORMAL;

  boolean ignoreCancelled() default true;
}
