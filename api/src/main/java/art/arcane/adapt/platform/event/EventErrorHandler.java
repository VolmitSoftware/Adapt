package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

/** Receives handler failures. The bus keeps dispatching to the remaining handlers. */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface EventErrorHandler {
  /**
   * Called on the posting thread. {@code owner} is the handler's owner, or the registration listener that failed, in
   * which case {@code event} is null. Exceptions thrown here are swallowed.
   */
  void handlerFailed(Object owner, AdaptEvent event, Throwable error);
}
