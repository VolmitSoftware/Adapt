package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

/** An event whose game action a handler can stop. What cancelling undoes is stated on each event type. */
@ApiStatus.AvailableSince("1.0.0")
public interface Cancellable {
  /** True when the action is cancelled, by a handler or by the platform before Adapt saw it. */
  boolean isCancelled();

  /**
   * Cancels the action, or with false lets a cancelled action go ahead. Later handlers registered with
   * {@code ignoreCancelled} are skipped while the flag is set.
   */
  void setCancelled(boolean cancelled);
}
