package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Base for events posted on {@link AdaptEventBus}.
 *
 * <p>Events are plain mutable objects built by a platform bridge for one dispatch. Handlers may change
 * the mutable fields and the cancelled flag; the bridge reads them back afterwards and applies them
 * to the native event or game state. Events must not be kept after the handler returns.
 *
 * <p>Handlers run synchronously on the thread that posts the event; for a game event that is the thread owning its
 * subject: the subject's region thread on Folia, the server thread elsewhere.
 *
 * <p>{@link #dispatchId()} identifies the platform event behind this object. A bridge that builds a
 * fresh core event for each priority of one native event gives all of them the same id, so handlers at
 * different priorities can pair up their work for that one occurrence.
 *
 * <p>{@link #dispatchCancelled()} reads the cancelled state of the platform event itself, so code that kept this
 * object past its handler (until a later event in the same action) sees the state other handlers left after this
 * priority ran, as a Bukkit handler holding the native event did.
 */
@ApiStatus.AvailableSince("1.0.0")
public abstract class AdaptEvent {
  private static final AtomicLong NEXT_DISPATCH_ID = new AtomicLong(1L);

  private long dispatchId = NEXT_DISPATCH_ID.getAndIncrement();
  private BooleanSupplier dispatchCancelled;
  private boolean protectionProbe;

  /** Takes a fresh {@link #dispatchId()}. */
  protected AdaptEvent() {
  }

  /** A fresh id, never returned before in this JVM. */
  public static long newDispatchId() {
    return NEXT_DISPATCH_ID.getAndIncrement();
  }

  /** Id of the platform event behind this object, shared by every copy a bridge posts for that one event. */
  public final long dispatchId() {
    return dispatchId;
  }

  /** Set by a platform bridge so the copies it posts for one native event share an id. Handlers do not call it. */
  @ApiStatus.Internal
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

  /** Set by a platform bridge to point {@link #dispatchCancelled()} at the native event. Handlers do not call it. */
  @ApiStatus.Internal
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

  /** Set by a platform bridge on an event posted for a protection check. Handlers do not call it. */
  @ApiStatus.Internal
  public final void markProtectionProbe() {
    this.protectionProbe = true;
  }
}
