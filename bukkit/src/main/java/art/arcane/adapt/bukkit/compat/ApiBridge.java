package art.arcane.adapt.bukkit.compat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method whose descriptor the Bukkit build copies into a core class, so plugins compiled against the
 * pre-port Bukkit-typed API still link. An instance bridge takes the target as its first parameter; the woven method
 * drops it and calls this method with {@code this}. Woven methods are synthetic, so new code compiles only against
 * the platform-typed members.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ApiBridge {
  Class<?> value();

  boolean instance() default true;

  String name() default "";
}
