package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** A living entity picks up a dropped item. Mutable: cancelled. */
public class EntityPickupItemEvent extends CancellableEvent {
  private final LivingRef entity;
  private final ItemEntityRef item;
  private final int remaining;

  public EntityPickupItemEvent(LivingRef entity, ItemEntityRef item, int remaining) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.item = Objects.requireNonNull(item, "item");
    this.remaining = remaining;
  }

  public LivingRef entity() {
    return entity;
  }

  public ItemEntityRef item() {
    return item;
  }

  /** Items left on the ground after the pickup. */
  public int remaining() {
    return remaining;
  }
}
