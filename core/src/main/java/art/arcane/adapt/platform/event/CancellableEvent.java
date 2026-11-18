package art.arcane.adapt.platform.event;

/** Base for events with a plain cancelled flag. */
public abstract class CancellableEvent extends AdaptEvent implements Cancellable {
  private boolean cancelled;

  protected CancellableEvent() {
  }

  protected CancellableEvent(boolean cancelled) {
    this.cancelled = cancelled;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }
}
