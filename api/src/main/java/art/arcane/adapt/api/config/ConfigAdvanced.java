package art.arcane.adapt.api.config;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a config field most admins never need; the in-game config menu hides it behind an "advanced" toggle. */
@ApiStatus.AvailableSince("1.0.0")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigAdvanced {
}
