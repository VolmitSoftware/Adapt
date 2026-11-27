package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A dropped item is about to merge into a nearby one. Cancelling keeps both. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class ItemMergeEvent extends CancellableEvent {
  private final ItemEntityRef item;
  private final ItemEntityRef target;

  public ItemMergeEvent(ItemEntityRef item, ItemEntityRef target) {
    this.item = Objects.requireNonNull(item, "item");
    this.target = Objects.requireNonNull(target, "target");
  }

  /** The item entity that merges away. */
  public ItemEntityRef item() {
    return item;
  }

  /** The item entity that takes in its stack. */
  public ItemEntityRef target() {
    return target;
  }
}
