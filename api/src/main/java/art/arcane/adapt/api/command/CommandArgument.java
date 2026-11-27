package art.arcane.adapt.api.command;

import art.arcane.adapt.api.text.Text;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One typed argument of an {@link AdaptCommand}. Declare it once as a constant and read its value with
 * {@link CommandInvocation#get}. The name is what {@code name=value} uses and what the help menu shows.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class CommandArgument<T> {
  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]*");

  private final String name;
  private final ArgumentType<T> type;
  private final Text help;
  private final boolean optional;

  private CommandArgument(String name, ArgumentType<T> type, Text help, boolean optional) {
    Objects.requireNonNull(name, "name");
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid argument name '" + name + "'");
    }
    this.name = name;
    this.type = Objects.requireNonNull(type, "type");
    this.help = Objects.requireNonNull(help, "help");
    this.optional = optional;
  }

  /**
   * An argument the caller must give; read it with {@link CommandInvocation#get}.
   *
   * @param name lower case, {@code [a-z0-9_-]}, starting with a letter
   * @param help one line describing the argument in the help menu
   * @throws IllegalArgumentException when the name is malformed
   */
  public static <T> CommandArgument<T> required(String name, ArgumentType<T> type, Text help) {
    return new CommandArgument<>(name, type, help, false);
  }

  /** An argument the caller may leave out; read it with {@link CommandInvocation#find}. */
  public static <T> CommandArgument<T> optional(String name, ArgumentType<T> type, Text help) {
    return new CommandArgument<>(name, type, help, true);
  }

  /** The name used in {@code name=value} and shown in help. */
  public String name() {
    return name;
  }

  /** How the argument's text is parsed and completed. */
  public ArgumentType<T> type() {
    return type;
  }

  /** The line describing the argument in the help menu. */
  public Text help() {
    return help;
  }

  /** Whether the caller may leave the argument out. */
  public boolean isOptional() {
    return optional;
  }

  @Override
  public String toString() {
    return optional ? "[" + name + "]" : "<" + name + ">";
  }
}
