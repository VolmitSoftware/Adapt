package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** A crafter block crafts. Mutable: cancelled. The crafter's grid is block().container(). */
public class CrafterCraftEvent extends CancellableEvent {
  private final BlockRef block;
  private final Id recipeKey;
  private final ItemRef result;

  public CrafterCraftEvent(BlockRef block, Id recipeKey, ItemRef result) {
    this.block = Objects.requireNonNull(block, "block");
    this.recipeKey = Objects.requireNonNull(recipeKey, "recipeKey");
    this.result = Objects.requireNonNull(result, "result");
  }

  public BlockRef block() {
    return block;
  }

  public Id recipeKey() {
    return recipeKey;
  }

  public ItemRef result() {
    return result;
  }
}
