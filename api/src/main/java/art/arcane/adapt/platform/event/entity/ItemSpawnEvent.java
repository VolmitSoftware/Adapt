package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A dropped item entity is about to be added to the world. Cancelling keeps it out. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class ItemSpawnEvent extends CancellableEvent {
  private final ItemEntityRef item;
  private final Loc location;

  public ItemSpawnEvent(ItemEntityRef item, Loc location) {
    this.item = Objects.requireNonNull(item, "item");
    this.location = Objects.requireNonNull(location, "location");
  }

  /** The item entity being added. */
  public ItemEntityRef item() {
    return item;
  }

  /** Where the item appears. */
  public Loc location() {
    return location;
  }
}
