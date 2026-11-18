package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.FurnaceRef;

public final class FakeFurnace implements FurnaceRef {
  private final BlockRef block;
  private ItemRef smelting = FakeItem.empty();
  private int burnTime;
  private int cookTime;
  private int cookTimeTotal;

  FakeFurnace(BlockRef block) {
    this.block = block;
  }

  @Override
  public BlockRef block() {
    return block;
  }

  @Override
  public int burnTime() {
    return burnTime;
  }

  public void setBurnTime(int ticks) {
    burnTime = ticks;
  }

  @Override
  public int cookTime() {
    return cookTime;
  }

  @Override
  public void setCookTime(int ticks) {
    cookTime = ticks;
  }

  @Override
  public int cookTimeTotal() {
    return cookTimeTotal;
  }

  public void setCookTimeTotal(int ticks) {
    cookTimeTotal = ticks;
  }

  @Override
  public ItemRef smelting() {
    return smelting;
  }

  public void setSmelting(ItemRef item) {
    smelting = item;
  }
}
