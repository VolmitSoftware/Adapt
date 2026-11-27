package art.arcane.adapt.api.command;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/**
 * How one argument's text becomes a value and which values tab completion offers. {@link ArgumentTypes} has the
 * common ones; implement this for anything else.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.OverrideOnly
public interface ArgumentType<T> {
  /** The type of the value, named in the help menu. */
  Class<T> valueType();

  /** The value the text stands for, or empty when it is not one; the caller is then told the text is invalid. */
  Optional<T> parse(CommandCaller caller, String text);

  /** Values to offer for what the caller has typed so far; Adapt keeps those that start with it. */
  List<String> suggest(CommandCaller caller, String typed);

  /** Whether this argument takes every remaining word, spaces included. Only the last argument may. */
  default boolean consumesRest() {
    return false;
  }
}
