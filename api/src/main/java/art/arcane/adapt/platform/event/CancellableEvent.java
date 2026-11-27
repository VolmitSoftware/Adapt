package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

/** Base for events with a plain cancelled flag. */
@ApiStatus.AvailableSince("1.0.0")
public abstract class CancellableEvent extends AdaptEvent implements Cancellable {
  private boolean cancelled;

  /** Starts not cancelled. */
  protected CancellableEvent() {
  }

  /** Starts with the given cancelled flag. */
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
