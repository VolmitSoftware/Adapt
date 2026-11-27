package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player starts mining a block. Mutable: instaBreak, cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class BlockDamageEvent extends PlayerEvent {
  private final BlockRef block;
  private final ItemRef item;
  private boolean instaBreak;

  public BlockDamageEvent(PlayerRef player, BlockRef block, ItemRef item, boolean instaBreak) {
    super(player);
    this.block = Objects.requireNonNull(block, "block");
    this.item = Objects.requireNonNull(item, "item");
    this.instaBreak = instaBreak;
  }

  /** The block the player started mining. */
  public BlockRef block() {
    return block;
  }

  /** The item in the player's hand. */
  public ItemRef item() {
    return item;
  }

  /** True when the block breaks at once instead of taking mining time. */
  public boolean instaBreak() {
    return instaBreak;
  }

  /** True makes the block break at once. */
  public void setInstaBreak(boolean instaBreak) {
    this.instaBreak = instaBreak;
  }
}
