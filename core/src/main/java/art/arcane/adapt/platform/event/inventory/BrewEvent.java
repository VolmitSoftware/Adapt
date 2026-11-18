package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A brewing stand finishes a brew. potions() holds the three bottle slots before brewing
 * (read-only); results() are the bottles after, in the same order. Mutable: results (set by index),
 * cancelled.
 */
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

  public BlockRef block() {
    return block;
  }

  public ItemRef ingredient() {
    return ingredient;
  }

  public List<ItemRef> potions() {
    return potions;
  }

  public List<ItemRef> results() {
    return results;
  }

  public int fuelLevel() {
    return fuelLevel;
  }

  public int brewingTime() {
    return brewingTime;
  }
}
