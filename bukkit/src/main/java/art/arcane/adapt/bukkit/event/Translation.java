package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Objects;

/**
 * Turns one Bukkit event class (the class that owns the handler list, subclasses included) into core
 * events. {@link #bridge} builds a fresh core event from the native event's current state, hands it to
 * the poster, then writes cancellation and every mutable field back. Translations hold no state and
 * run on whichever thread fired the native event.
 */
public abstract class Translation<B extends Event> {
  private final Class<B> nativeType;
  private final List<Class<? extends AdaptEvent>> posted;

  protected Translation(Class<B> nativeType, List<Class<? extends AdaptEvent>> posted) {
    this.nativeType = Objects.requireNonNull(nativeType, "nativeType");
    this.posted = List.copyOf(posted);
  }

  public final Class<B> nativeType() {
    return nativeType;
  }

  /** Every core class this translation may post. The bridge listens while any of them has handlers. */
  public final List<Class<? extends AdaptEvent>> posted() {
    return posted;
  }

  /** Core class that this native event would be posted as, or null to skip it. Must not allocate. */
  public Class<? extends AdaptEvent> target(B event) {
    return posted.getFirst();
  }

  /** Cancelled state as the core event will report it. */
  public boolean cancelled(B event) {
    return event instanceof Cancellable cancellable && cancellable.isCancelled();
  }

  public abstract void bridge(B event, Poster poster);

  /** Dispatches a translated event, normally to one bus priority. */
  @FunctionalInterface
  public interface Poster {
    void post(AdaptEvent event);
  }
}
