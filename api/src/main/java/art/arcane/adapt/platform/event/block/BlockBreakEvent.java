package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player breaks a block. The block is still in place during dispatch. Mutable: dropItems,
 * experience, cancelled (the block stays). Breaks Adapt starts itself through {@code ProtectionHost.attemptBreak} are
 * not posted, so veinminer-style adaptations do not recurse.
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The block being broken, still in its old state. */
  public BlockRef block() {
    return block;
  }

  /** True when the block will drop its items. */
  public boolean dropItems() {
    return dropItems;
  }

  /** False stops the item drops. Experience is controlled separately. */
  public void setDropItems(boolean dropItems) {
    this.dropItems = dropItems;
  }

  /** Experience the break will drop. */
  public int experience() {
    return experience;
  }

  /** Changes the experience dropped. Negative values become 0. */
  public void setExperience(int experience) {
    this.experience = Math.max(0, experience);
  }
}
