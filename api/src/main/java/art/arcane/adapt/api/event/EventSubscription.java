package art.arcane.adapt.api.event;

import org.jetbrains.annotations.ApiStatus;

/** A listener subscribed through {@link EventRegistry#subscribe(Object)}; keep it to unsubscribe. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface EventSubscription {
  /** The object that was subscribed. */
  Object listener();

  /** Whether the listener still receives events. */
  boolean isActive();

  /** Stops delivery for good. Safe to call twice. */
  void unsubscribe();
}
