package art.arcane.adapt.api.content;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets a skill's or adaptation's {@code @Handle} method see events another handler already cancelled, deaths the
 * platform cancelled, and interactions whose use was denied. Without it a component handler gets none of those,
 * whatever its {@code @Handle} says.
 *
 * <p>Player interactions are the exception: Bukkit posts air clicks already cancelled, so a
 * {@code PlayerInteractEvent} handler always receives cancelled interactions. Without this annotation it only skips
 * those whose block or item use was denied.
 */
@ApiStatus.AvailableSince("1.0.0")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReceiveCancelledEvents {
}
