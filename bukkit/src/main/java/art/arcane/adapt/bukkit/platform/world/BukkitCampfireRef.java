package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.CampfireRef;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;

import java.util.Objects;

public final class BukkitCampfireRef implements CampfireRef {
  private final Block block;

  public BukkitCampfireRef(Block block) {
    this.block = Objects.requireNonNull(block, "block");
  }

  @Override
  public BlockRef block() {
    return BukkitRefs.block(block);
  }

  @Override
  public int size() {
    Campfire campfire = snapshot();
    return campfire == null ? 0 : campfire.getSize();
  }

  @Override
  public ItemRef item(int slot) {
    Campfire campfire = snapshot();
    return BukkitRefs.item(campfire == null ? null : campfire.getItem(slot));
  }

  @Override
  public int cookTime(int slot) {
    Campfire campfire = snapshot();
    return campfire == null ? 0 : campfire.getCookTime(slot);
  }

  @Override
  public void setCookTime(int slot, int ticks) {
    Campfire campfire = snapshot();
    if (campfire == null) {
      return;
    }
    campfire.setCookTime(slot, ticks);
    campfire.update(true, true);
  }

  @Override
  public int cookTimeTotal(int slot) {
    Campfire campfire = snapshot();
    return campfire == null ? 0 : campfire.getCookTimeTotal(slot);
  }

  private Campfire snapshot() {
    return block.getState() instanceof Campfire campfire ? campfire : null;
  }
}
