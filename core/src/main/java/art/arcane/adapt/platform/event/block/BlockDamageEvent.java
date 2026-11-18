package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** A player starts mining a block. Mutable: instaBreak, cancelled. */
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

  public BlockRef block() {
    return block;
  }

  public ItemRef item() {
    return item;
  }

  public boolean instaBreak() {
    return instaBreak;
  }

  public void setInstaBreak(boolean instaBreak) {
    this.instaBreak = instaBreak;
  }
}
