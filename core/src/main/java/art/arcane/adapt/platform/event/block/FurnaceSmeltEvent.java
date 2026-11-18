package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

/**
 * A furnace, smoker or blast furnace finishes smelting one item. Handlers of BlockCookEvent receive
 * it too. Mutable: result, cancelled.
 */
public class FurnaceSmeltEvent extends BlockCookEvent {
  public FurnaceSmeltEvent(BlockRef block, ItemRef source, ItemRef result) {
    super(block, source, result);
  }
}
