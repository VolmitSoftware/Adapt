package art.arcane.adapt.api.content;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets an adaptation's move or jump handler run for players who have not learned it. Those two events are frequent
 * enough that Adapt only delivers them for online learners unless the handler is marked.
 */
@ApiStatus.AvailableSince("1.0.0")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RunsWithoutLearnedAdaptation {
}
