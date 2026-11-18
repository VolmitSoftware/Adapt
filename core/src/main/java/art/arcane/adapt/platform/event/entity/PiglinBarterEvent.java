package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A piglin finishes bartering. Mutable: outcome list (add, remove or replace items), cancelled.
 */
public class PiglinBarterEvent extends CancellableEvent {
  private final EntityRef piglin;
  private final ItemRef input;
  private final List<ItemRef> outcome;

  public PiglinBarterEvent(EntityRef piglin, ItemRef input, List<ItemRef> outcome) {
    this.piglin = Objects.requireNonNull(piglin, "piglin");
    this.input = Objects.requireNonNull(input, "input");
    this.outcome = new ArrayList<>(outcome);
  }

  public EntityRef piglin() {
    return piglin;
  }

  public ItemRef input() {
    return input;
  }

  public List<ItemRef> outcome() {
    return outcome;
  }
}
