package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.item.ItemRef;

/** A campfire or soul campfire block entity. Setters write through to the block. */
public interface CampfireRef {
  BlockRef block();

  int size();

  /** The item cooking in a slot (empty ref when none). */
  ItemRef item(int slot);

  int cookTime(int slot);

  void setCookTime(int slot, int ticks);

  int cookTimeTotal(int slot);
}
