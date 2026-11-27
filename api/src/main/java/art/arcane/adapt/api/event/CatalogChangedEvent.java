package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * The set of registered content changed: an addon was applied or removed, a config reload enabled or disabled
 * something, or a veto was placed or lifted. Anything cached from the catalog is stale.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class CatalogChangedEvent extends AdaptEvent {
  private final long revision;

  public CatalogChangedEvent(long revision) {
    this.revision = revision;
  }

  /** The catalog's new revision, as {@code ContentCatalog.revision()} now reports it. */
  public long revision() {
    return revision;
  }
}
