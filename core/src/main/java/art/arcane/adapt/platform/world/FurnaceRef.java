package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.item.ItemRef;

/** A furnace, blast furnace or smoker block entity. Setters write through to the block. */
public interface FurnaceRef {
  BlockRef block();

  /** Ticks of fuel left in the current burn. */
  int burnTime();

  /** Ticks the current item has been cooking. */
  int cookTime();

  void setCookTime(int ticks);

  /** Ticks the current item needs in total. */
  int cookTimeTotal();

  /** The item in the input slot (empty ref when none). */
  ItemRef smelting();
}
