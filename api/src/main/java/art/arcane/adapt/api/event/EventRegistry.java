package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Subscribes listeners to Adapt's event bus: the progress events of this package and every platform event in
 * {@code art.arcane.adapt.platform.event}. One listener works on every platform.
 *
 * <p>Available at any time, also before Adapt has started. A subscription holds across core restarts until it is
 * cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface EventRegistry {
  /**
   * Subscribes every method of {@code listener} annotated with {@code @Handle} that takes one event.
   *
   * @throws IllegalArgumentException when the object is already subscribed
   */
  EventSubscription subscribe(Object listener);

  /**
   * Posts {@code event} to every handler of its type, on this thread, while Adapt runs; does nothing otherwise. For the
   * events an addon defines itself, so other addons can listen to them. Adapt's own events, those of the
   * {@code art.arcane.adapt.api} and {@code art.arcane.adapt.platform} packages, are posted by Adapt alone, so a
   * listener can trust that a level-up or a block break really happened.
   *
   * @return {@code event}, after every handler saw it
   * @throws IllegalArgumentException for one of Adapt's own events
   */
  @ApiStatus.Experimental
  <E extends AdaptEvent> E post(E event);
}
