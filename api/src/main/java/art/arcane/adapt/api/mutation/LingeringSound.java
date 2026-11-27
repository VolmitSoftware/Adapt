package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

/** The quiet tick of a running effect, started by {@code linger}. {@link #stop()} silences it and plays a short tail. */
@ApiStatus.Experimental
public interface LingeringSound {
  /** Whether the tick still plays, about once a second; false once {@link #stop()} was called. */
  boolean isRunning();

  /** Ends the tick and plays the tail; later calls do nothing. */
  void stop();
}
