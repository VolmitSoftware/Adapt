package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A piglin finishes bartering and is about to throw its outcome. Cancelling throws nothing. Mutable: outcome list (add,
 * remove or replace items), cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PiglinBarterEvent extends CancellableEvent {
  private final EntityRef piglin;
  private final ItemRef input;
  private final List<ItemRef> outcome;

  public PiglinBarterEvent(EntityRef piglin, ItemRef input, List<ItemRef> outcome) {
    this.piglin = Objects.requireNonNull(piglin, "piglin");
    this.input = Objects.requireNonNull(input, "input");
    this.outcome = new ArrayList<>(outcome);
  }

  /** The bartering piglin. */
  public EntityRef piglin() {
    return piglin;
  }

  /** The item the piglin was given. */
  public ItemRef input() {
    return input;
  }

  /** Items the piglin will throw, editable. Null and empty entries are skipped. */
  public List<ItemRef> outcome() {
    return outcome;
  }
}
