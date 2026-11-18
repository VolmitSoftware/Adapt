package art.arcane.adapt.platform.event;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Base for events posted on {@link AdaptEventBus}.
 *
 * <p>Events are plain mutable objects built by a platform bridge for one dispatch. Handlers may change
 * the mutable fields and the cancelled flag; the bridge reads them back afterwards and applies them
 * to the native event or game state. Events must not be kept after the handler returns.
 *
 * <p>{@link #dispatchId()} identifies the platform event behind this object. A bridge that builds a
 * fresh core event for each priority of one native event gives all of them the same id, so handlers at
 * different priorities can pair up their work for that one occurrence.
 *
 * <p>{@link #dispatchCancelled()} reads the cancelled state of the platform event itself, so code that kept this
 * object past its handler (until a later event in the same action) sees the state other handlers left after this
 * priority ran, as a Bukkit handler holding the native event did.
 */
public abstract class AdaptEvent {
  private static final AtomicLong NEXT_DISPATCH_ID = new AtomicLong(1L);

  private long dispatchId = NEXT_DISPATCH_ID.getAndIncrement();
  private BooleanSupplier dispatchCancelled;
  private boolean protectionProbe;

  protected AdaptEvent() {
  }

  /** A fresh id, never returned before in this JVM. */
  public static long newDispatchId() {
    return NEXT_DISPATCH_ID.getAndIncrement();
  }

  public final long dispatchId() {
    return dispatchId;
  }

  public final void setDispatchId(long dispatchId) {
    this.dispatchId = dispatchId;
  }

  /**
   * Cancelled state of the platform event behind this object, read now. A bridge that posts a copy per priority
   * points it at the native event; otherwise it is this object's own cancelled flag.
   */
  public final boolean dispatchCancelled() {
    BooleanSupplier view = dispatchCancelled;
    if (view != null) {
      return view.getAsBoolean();
    }
    return this instanceof Cancellable cancellable && cancellable.isCancelled();
  }

  public final void setDispatchCancelled(BooleanSupplier view) {
    this.dispatchCancelled = view;
  }

  /**
   * True when a protection check fired the platform event only to ask other plugins whether the action is allowed:
   * nothing happens in the world. Skills and adaptations never receive these; other handlers do and decide.
   */
  public final boolean protectionProbe() {
    return protectionProbe;
  }

  public final void markProtectionProbe() {
    this.protectionProbe = true;
  }
}
