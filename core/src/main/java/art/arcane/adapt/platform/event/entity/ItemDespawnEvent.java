package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** A dropped item despawns from age. Mutable: cancelled. */
public class ItemDespawnEvent extends CancellableEvent {
  private final ItemEntityRef item;
  private final Loc location;

  public ItemDespawnEvent(ItemEntityRef item, Loc location) {
    this.item = Objects.requireNonNull(item, "item");
    this.location = Objects.requireNonNull(location, "location");
  }

  public ItemEntityRef item() {
    return item;
  }

  public Loc location() {
    return location;
  }
}
