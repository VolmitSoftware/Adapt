package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/**
 * A furnace, smoker, blast furnace or campfire finishes cooking one item. Mutable: result,
 * cancelled.
 */
public class BlockCookEvent extends CancellableEvent {
  private final BlockRef block;
  private final ItemRef source;
  private ItemRef result;

  public BlockCookEvent(BlockRef block, ItemRef source, ItemRef result) {
    this.block = Objects.requireNonNull(block, "block");
    this.source = Objects.requireNonNull(source, "source");
    this.result = Objects.requireNonNull(result, "result");
  }

  public BlockRef block() {
    return block;
  }

  public ItemRef source() {
    return source;
  }

  public ItemRef result() {
    return result;
  }

  public void setResult(ItemRef result) {
    this.result = Objects.requireNonNull(result, "result");
  }
}
