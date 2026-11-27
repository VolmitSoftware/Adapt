package art.arcane.adapt.api.command;

import org.jetbrains.annotations.ApiStatus;

/** What an {@link AdaptCommand} does when it runs. */
@ApiStatus.AvailableSince("1.0.0")
@FunctionalInterface
public interface CommandAction {
  /**
   * Runs on the server's global thread, as Adapt's own commands do; on Folia that is the global region thread, so
   * work on a player goes through {@code AdaptApi.scheduler().runEntity}. An exception is reported to the caller and
   * logged; it never reaches the platform.
   */
  void run(CommandInvocation invocation);
}
