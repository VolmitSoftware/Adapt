package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;

import java.util.Objects;

public final class BukkitFurnaceRef implements FurnaceRef {
  private final Block block;

  public BukkitFurnaceRef(Block block) {
    this.block = Objects.requireNonNull(block, "block");
  }

  @Override
  public BlockRef block() {
    return BukkitRefs.block(block);
  }

  @Override
  public int burnTime() {
    Furnace furnace = snapshot();
    return furnace == null ? 0 : furnace.getBurnTime();
  }

  @Override
  public int cookTime() {
    Furnace furnace = snapshot();
    return furnace == null ? 0 : furnace.getCookTime();
  }

  @Override
  public void setCookTime(int ticks) {
    Furnace furnace = snapshot();
    if (furnace == null) {
      return;
    }
    furnace.setCookTime((short) ticks);
    furnace.update(true, true);
  }

  @Override
  public int cookTimeTotal() {
    Furnace furnace = snapshot();
    return furnace == null ? 0 : furnace.getCookTimeTotal();
  }

  @Override
  public ItemRef smelting() {
    Furnace furnace = snapshot();
    return BukkitRefs.item(furnace == null ? null : furnace.getInventory().getSmelting());
  }

  private Furnace snapshot() {
    return block.getState() instanceof Furnace furnace ? furnace : null;
  }
}
