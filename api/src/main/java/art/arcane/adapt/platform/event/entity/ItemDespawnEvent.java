package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A dropped item is about to despawn because it reached its age limit. Cancelling keeps it; mod loaders restart its
 * age.
 */
@ApiStatus.AvailableSince("1.0.0")
public class ItemDespawnEvent extends CancellableEvent {
  private final ItemEntityRef item;
  private final Loc location;

  public ItemDespawnEvent(ItemEntityRef item, Loc location) {
    this.item = Objects.requireNonNull(item, "item");
    this.location = Objects.requireNonNull(location, "location");
  }

  /** The despawning item entity. */
  public ItemEntityRef item() {
    return item;
  }

  /** Where the item is. */
  public Loc location() {
    return location;
  }
}
