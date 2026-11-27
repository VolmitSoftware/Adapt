package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A furnace, smoker, blast furnace or campfire finishes cooking one item. Mutable: result,
 * cancelled (the item is not cooked).
 */
@ApiStatus.AvailableSince("1.0.0")
public class BlockCookEvent extends CancellableEvent {
  private final BlockRef block;
  private final ItemRef source;
  private ItemRef result;

  public BlockCookEvent(BlockRef block, ItemRef source, ItemRef result) {
    this.block = Objects.requireNonNull(block, "block");
    this.source = Objects.requireNonNull(source, "source");
    this.result = Objects.requireNonNull(result, "result");
  }

  /** The furnace, smoker, blast furnace or campfire. */
  public BlockRef block() {
    return block;
  }

  /** The item being cooked. */
  public ItemRef source() {
    return source;
  }

  /** The item the cook produces. */
  public ItemRef result() {
    return result;
  }

  /** Replaces the produced item. Must not be null. */
  public void setResult(ItemRef result) {
    this.result = Objects.requireNonNull(result, "result");
  }
}
