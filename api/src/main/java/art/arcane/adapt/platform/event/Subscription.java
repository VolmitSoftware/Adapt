package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

/** One registered handler. */
@ApiStatus.AvailableSince("1.0.0")
public interface Subscription {
  /** The owner passed at registration. */
  Object owner();

  /** Event class the handler was registered for. Subtypes reach it too. */
  Class<? extends AdaptEvent> eventType();

  /** Priority the handler runs at. */
  EventPriority priority();

  /** True when the handler is skipped while the event is cancelled. */
  boolean ignoreCancelled();

  /** False once the handler is removed, by {@link #unregister()} or through the bus. */
  boolean isActive();

  /** Removes this handler. Idempotent. A dispatch already in progress skips it from then on. */
  void unregister();
}
