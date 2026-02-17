package art.arcane.adapt.util.reflect.events.api;

import org.bukkit.event.EventPriority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReflectiveHandler {
    EventPriority priority() default EventPriority.NORMAL;

    boolean ignoreCancelled() default false;
}
