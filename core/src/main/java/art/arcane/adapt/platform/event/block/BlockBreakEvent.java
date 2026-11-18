package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/**
 * A player breaks a block. The block is still in place during dispatch. Mutable: dropItems,
 * experience, cancelled. Breaks Adapt starts itself through {@code ProtectionHost.attemptBreak} are
 * not posted, so veinminer-style adaptations do not recurse.
 */
public class BlockBreakEvent extends PlayerEvent {
  private final BlockRef block;
  private boolean dropItems;
  private int experience;

  public BlockBreakEvent(PlayerRef player, BlockRef block, boolean dropItems, int experience) {
    super(player);
    this.block = Objects.requireNonNull(block, "block");
    this.dropItems = dropItems;
    this.experience = experience;
  }

  public BlockRef block() {
    return block;
  }

  public boolean dropItems() {
    return dropItems;
  }

  public void setDropItems(boolean dropItems) {
    this.dropItems = dropItems;
  }

  public int experience() {
    return experience;
  }

  public void setExperience(int experience) {
    this.experience = Math.max(0, experience);
  }
}
