package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A living entity is about to pick up a dropped item. Cancelling leaves the item on the ground. For a player it is
 * posted only when some of the stack fits in the inventory.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityPickupItemEvent extends CancellableEvent {
  private final LivingRef entity;
  private final ItemEntityRef item;
  private final int remaining;

  public EntityPickupItemEvent(LivingRef entity, ItemEntityRef item, int remaining) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.item = Objects.requireNonNull(item, "item");
    this.remaining = remaining;
  }

  /** The entity picking the item up. */
  public LivingRef entity() {
    return entity;
  }

  /** The dropped item entity. */
  public ItemEntityRef item() {
    return item;
  }

  /** Items left on the ground after the pickup, 0 when the whole stack is taken. Mod loaders report 0 for mobs. */
  public int remaining() {
    return remaining;
  }
}
