package art.arcane.adapt.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The node a sender needs for a Director command of {@code /adapt}. On a class it covers every command in it that
 * names no node of its own. {@link AdaptCommands} refuses, hides from completion and leaves out of help every command
 * whose node the sender lacks.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CommandPermission {
  String value();
}
