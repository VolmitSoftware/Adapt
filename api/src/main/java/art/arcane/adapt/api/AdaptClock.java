package art.arcane.adapt.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Wall time and server ticks as Adapt sees them. Read time through {@link AdaptApi#clock()} instead of
 * {@link System#currentTimeMillis()} so tests that drive Adapt's clock also drive your content.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface AdaptClock {
  /** Milliseconds since the epoch. */
  long millis();

  /** The server tick counter; 0 when no core runs. */
  long tick();

  /**
   * How much of its per-tick time budget Adapt's own ticked work used over the last few seconds, in percent; 0 when
   * no core runs. Content can shed optional work, such as previews, while it is high.
   */
  @ApiStatus.Experimental
  default double loadPercent() {
    return 0D;
  }
}
