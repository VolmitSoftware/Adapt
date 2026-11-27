package art.arcane.adapt.api.command;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/** One run of an {@link AdaptCommand}: who ran it and the parsed values of its arguments. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface CommandInvocation {
  /** Who ran the command. */
  CommandCaller caller();

  /** The words that led here, such as {@code [adapt, geology, status]}. */
  List<String> path();

  /**
   * The value of a required argument, or of an optional one that was given. Throws
   * {@link IllegalArgumentException} for an argument the command does not declare and {@link IllegalStateException}
   * for an optional one that was left out.
   */
  <T> T get(CommandArgument<T> argument);

  /** The value of an argument, empty when an optional one was left out. */
  <T> Optional<T> find(CommandArgument<T> argument);
}
