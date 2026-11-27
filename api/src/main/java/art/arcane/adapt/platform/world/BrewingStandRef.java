package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Brewing stand at a block. Reads and writes go to the live block entity; getters never return null (empty slots
 * are an empty {@link ItemRef}). Same thread rules as {@link BlockRef}.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface BrewingStandRef {
  /** Number of bottle slots. */
  int BOTTLE_SLOTS = 3;

  /** Block the stand sits at. */
  BlockRef block();

  /** Bottle slot 0 to 2. */
  ItemRef bottle(int slot);

  /** Replaces the item in bottle slot 0 to 2. */
  void setBottle(int slot, ItemRef item);

  /** Item in the ingredient slot. */
  ItemRef ingredient();

  /** Replaces the ingredient. */
  void setIngredient(ItemRef item);

  /** Item in the fuel slot. */
  ItemRef fuel();

  /** Replaces the item in the fuel slot. */
  void setFuel(ItemRef item);

  /** Stored fuel charges (one blaze powder gives 20). */
  int fuelLevel();

  /** Sets the stored fuel charges. */
  void setFuelLevel(int level);

  /** Remaining brew ticks; 0 when idle. */
  int brewingTime();

  /** Sets the remaining brew ticks. */
  void setBrewingTime(int ticks);

  /** Players with this stand's inventory open. */
  List<PlayerRef> viewers();
}
