package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * A furnace, smoker or blast furnace finishes smelting one item. Handlers of BlockCookEvent receive
 * it too. Mutable: result, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class FurnaceSmeltEvent extends BlockCookEvent {
  public FurnaceSmeltEvent(BlockRef block, ItemRef source, ItemRef result) {
    super(block, source, result);
  }
}
