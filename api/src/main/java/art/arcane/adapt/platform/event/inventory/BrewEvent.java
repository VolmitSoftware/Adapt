package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A brewing stand finishes a brew. potions() holds the three bottle slots before brewing
 * (read-only); results() are the bottles after, in the same order. Mutable: results (set by index),
 * cancelled (the brew is undone).
 */
@ApiStatus.AvailableSince("1.0.0")
public class BrewEvent extends CancellableEvent {
  private final BlockRef block;
  private final ItemRef ingredient;
  private final List<ItemRef> potions;
  private final List<ItemRef> results;
  private final int fuelLevel;
  private final int brewingTime;

  public BrewEvent(BlockRef block, ItemRef ingredient, List<ItemRef> potions, List<ItemRef> results, int fuelLevel, int brewingTime) {
    this.block = Objects.requireNonNull(block, "block");
    this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
    this.potions = List.copyOf(potions);
    this.results = new ArrayList<>(results);
    this.fuelLevel = fuelLevel;
    this.brewingTime = brewingTime;
  }

  /** The brewing stand. */
  public BlockRef block() {
    return block;
  }

  /** The ingredient slot's item before brewing. */
  public ItemRef ingredient() {
    return ingredient;
  }

  /** Read-only copy of the three bottle slots before brewing. */
  public List<ItemRef> potions() {
    return potions;
  }

  /** The live list of resulting bottles. Replace an entry to change that bottle. */
  public List<ItemRef> results() {
    return results;
  }

  /** Fuel left in the stand, counted in brews. */
  public int fuelLevel() {
    return fuelLevel;
  }

  /** Brewing time the stand reports, in ticks. */
  public int brewingTime() {
    return brewingTime;
  }
}
