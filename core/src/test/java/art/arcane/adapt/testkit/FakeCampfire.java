package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.CampfireRef;

public final class FakeCampfire implements CampfireRef {
  private static final int SIZE = 4;
  private final BlockRef block;
  private final ItemRef[] items = {FakeItem.empty(), FakeItem.empty(), FakeItem.empty(), FakeItem.empty()};
  private final int[] cookTimes = new int[SIZE];
  private final int[] cookTimeTotals = new int[SIZE];

  FakeCampfire(BlockRef block) {
    this.block = block;
  }

  @Override
  public BlockRef block() {
    return block;
  }

  @Override
  public int size() {
    return SIZE;
  }

  @Override
  public ItemRef item(int slot) {
    return items[slot];
  }

  public void setItem(int slot, ItemRef item, int cookTimeTotal) {
    items[slot] = item;
    cookTimeTotals[slot] = cookTimeTotal;
  }

  @Override
  public int cookTime(int slot) {
    return cookTimes[slot];
  }

  @Override
  public void setCookTime(int slot, int ticks) {
    cookTimes[slot] = ticks;
  }

  @Override
  public int cookTimeTotal(int slot) {
    return cookTimeTotals[slot];
  }
}
