package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

/**
 * A handler registered on {@link AdaptEventBus}. An exception it throws goes to the bus's {@link EventErrorHandler},
 * and the remaining handlers still run.
 */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface EventListener<E> {
  /** Called once for each posted event, on the thread that posted it. */
  void handle(E event) throws Exception;
}
