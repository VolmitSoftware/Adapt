package art.arcane.adapt.platform;

import org.jetbrains.annotations.ApiStatus;

/** A scheduled task. Cancelling is idempotent and safe from any thread. */
@ApiStatus.AvailableSince("1.0.0")
public interface TaskHandle {
  /** Stops the task; a run already in progress finishes. */
  void cancel();

  /** True once the task was cancelled, retired with its entity, or could not be scheduled. */
  boolean isCancelled();
}
