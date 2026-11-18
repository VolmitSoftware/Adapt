package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** A dropped item entity spawns. Mutable: cancelled. */
public class ItemSpawnEvent extends CancellableEvent {
  private final ItemEntityRef item;
  private final Loc location;

  public ItemSpawnEvent(ItemEntityRef item, Loc location) {
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
