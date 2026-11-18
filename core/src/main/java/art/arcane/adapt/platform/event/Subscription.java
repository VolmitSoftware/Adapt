package art.arcane.adapt.platform.event;

/** One registered handler. */
public interface Subscription {
  Object owner();

  Class<? extends AdaptEvent> eventType();

  EventPriority priority();

  boolean ignoreCancelled();

  boolean isActive();

  /** Removes this handler. Idempotent. A dispatch already in progress skips it from then on. */
  void unregister();
}
