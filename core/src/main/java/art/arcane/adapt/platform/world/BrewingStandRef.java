package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;

/**
 * Brewing stand at a block. Reads and writes go to the live block entity; getters never return null (empty slots
 * are an empty {@link ItemRef}). Same thread rules as {@link BlockRef}.
 */
public interface BrewingStandRef {
  int BOTTLE_SLOTS = 3;

  BlockRef block();

  /** Bottle slot 0 to 2. */
  ItemRef bottle(int slot);

  void setBottle(int slot, ItemRef item);

  ItemRef ingredient();

  void setIngredient(ItemRef item);

  ItemRef fuel();

  void setFuel(ItemRef item);

  /** Stored fuel charges (one blaze powder gives 20). */
  int fuelLevel();

  void setFuelLevel(int level);

  /** Remaining brew ticks; 0 when idle. */
  int brewingTime();

  void setBrewingTime(int ticks);

  /** Players with this stand's inventory open. */
  List<PlayerRef> viewers();
}
