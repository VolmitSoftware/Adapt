package art.arcane.adapt.api.config;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Describes what a config class configures; written at the top of its generated file. */
@ApiStatus.AvailableSince("1.0.0")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigDescription {
  /** The description, in English. */
  String value();
}
