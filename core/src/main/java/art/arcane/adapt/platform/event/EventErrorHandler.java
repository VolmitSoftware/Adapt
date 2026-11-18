package art.arcane.adapt.platform.event;

/** Receives handler failures. The bus keeps dispatching to the remaining handlers. */
@FunctionalInterface
public interface EventErrorHandler {
  void handlerFailed(Object owner, AdaptEvent event, Throwable error);
}
