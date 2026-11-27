package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

/** A campfire or soul campfire block entity. Setters write through to the block. */
@ApiStatus.AvailableSince("1.0.0")
public interface CampfireRef {
  /** Block the campfire sits at. */
  BlockRef block();

  /** Number of cooking slots; 0 when the block entity is gone. */
  int size();

  /** The item cooking in a slot (empty ref when none). */
  ItemRef item(int slot);

  /** Ticks the item in the slot has cooked. */
  int cookTime(int slot);

  /** Sets the ticks the item in the slot has cooked. */
  void setCookTime(int slot, int ticks);

  /** Ticks the item in the slot needs in total. */
  int cookTimeTotal(int slot);
}
