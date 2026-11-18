package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** A dropped item merges into another. Mutable: cancelled. */
public class ItemMergeEvent extends CancellableEvent {
  private final ItemEntityRef item;
  private final ItemEntityRef target;

  public ItemMergeEvent(ItemEntityRef item, ItemEntityRef target) {
    this.item = Objects.requireNonNull(item, "item");
    this.target = Objects.requireNonNull(target, "target");
  }

  public ItemEntityRef item() {
    return item;
  }

  public ItemEntityRef target() {
    return target;
  }
}
