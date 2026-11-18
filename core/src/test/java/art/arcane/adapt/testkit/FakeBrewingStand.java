package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;

import java.util.ArrayList;
import java.util.List;

/** Brewing stand state held by a {@link FakeWorld}: three bottles, ingredient, fuel, fuel level, brew time, viewers. */
public final class FakeBrewingStand implements BrewingStandRef {
  private final BlockRef block;
  private final ItemRef[] bottles = {FakeItem.empty(), FakeItem.empty(), FakeItem.empty()};
  private final List<PlayerRef> viewers = new ArrayList<>();
  private ItemRef ingredient = FakeItem.empty();
  private ItemRef fuel = FakeItem.empty();
  private int fuelLevel;
  private int brewingTime;

  FakeBrewingStand(BlockRef block) {
    this.block = block;
  }

  /** Adds a player to {@link #viewers()}. */
  public FakeBrewingStand view(PlayerRef player) {
    viewers.add(player);
    return this;
  }

  @Override
  public BlockRef block() {
    return block;
  }

  @Override
  public ItemRef bottle(int slot) {
    return bottles[slot];
  }

  @Override
  public void setBottle(int slot, ItemRef item) {
    bottles[slot] = item == null ? FakeItem.empty() : item;
  }

  @Override
  public ItemRef ingredient() {
    return ingredient;
  }

  @Override
  public void setIngredient(ItemRef item) {
    ingredient = item == null ? FakeItem.empty() : item;
  }

  @Override
  public ItemRef fuel() {
    return fuel;
  }

  @Override
  public void setFuel(ItemRef item) {
    fuel = item == null ? FakeItem.empty() : item;
  }

  @Override
  public int fuelLevel() {
    return fuelLevel;
  }

  @Override
  public void setFuelLevel(int level) {
    fuelLevel = level;
  }

  @Override
  public int brewingTime() {
    return brewingTime;
  }

  @Override
  public void setBrewingTime(int ticks) {
    brewingTime = ticks;
  }

  @Override
  public List<PlayerRef> viewers() {
    return List.copyOf(viewers);
  }
}
