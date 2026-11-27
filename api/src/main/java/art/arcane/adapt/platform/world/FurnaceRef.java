package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

/** A furnace, blast furnace or smoker block entity. Setters write through to the block. */
@ApiStatus.AvailableSince("1.0.0")
public interface FurnaceRef {
  /** Block the furnace sits at. */
  BlockRef block();

  /** Ticks of fuel left in the current burn. */
  int burnTime();

  /** Ticks the current item has been cooking. */
  int cookTime();

  /** Sets the ticks the current item has been cooking. */
  void setCookTime(int ticks);

  /** Ticks the current item needs in total. */
  int cookTimeTotal();

  /** The item in the input slot (empty ref when none). */
  ItemRef smelting();
}
