package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A crafter block crafts. Mutable: cancelled (nothing is crafted). The crafter's grid is block().container(). */
@ApiStatus.AvailableSince("1.0.0")
public class CrafterCraftEvent extends CancellableEvent {
  private final BlockRef block;
  private final Id recipeKey;
  private final ItemRef result;

  public CrafterCraftEvent(BlockRef block, Id recipeKey, ItemRef result) {
    this.block = Objects.requireNonNull(block, "block");
    this.recipeKey = Objects.requireNonNull(recipeKey, "recipeKey");
    this.result = Objects.requireNonNull(result, "result");
  }

  /** The crafter block. */
  public BlockRef block() {
    return block;
  }

  /** Id of the recipe being crafted. */
  public Id recipeKey() {
    return recipeKey;
  }

  /** The item the crafter outputs. */
  public ItemRef result() {
    return result;
  }
}
