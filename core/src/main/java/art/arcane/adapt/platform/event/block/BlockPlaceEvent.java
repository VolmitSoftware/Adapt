package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;

/**
 * A player placed a block. The new block is in the world during dispatch. Mutable: cancelled (reverts) and
 * build: false reverts the placement like a cancel, the way Bukkit's {@code setBuild(false)} does.
 */
public class BlockPlaceEvent extends PlayerEvent {
  private final BlockRef block;
  private final BlockStateRef replacedState;
  private final BlockRef against;
  private final ItemRef itemInHand;
  private final Hand hand;
  private boolean build = true;

  public BlockPlaceEvent(PlayerRef player, BlockRef block, BlockStateRef replacedState, BlockRef against,
                         ItemRef itemInHand, Hand hand) {
    super(player);
    this.block = Objects.requireNonNull(block, "block");
    this.replacedState = Objects.requireNonNull(replacedState, "replacedState");
    this.against = against;
    this.itemInHand = Objects.requireNonNull(itemInHand, "itemInHand");
    this.hand = hand == null ? Hand.MAIN_HAND : hand;
  }

  public BlockRef block() {
    return block;
  }

  public BlockStateRef replacedState() {
    return replacedState;
  }

  public BlockRef against() {
    return against;
  }

  /** The placed item as it was before placement (still carries its custom data). */
  public ItemRef itemInHand() {
    return itemInHand;
  }

  public Hand hand() {
    return hand;
  }

  /** False when the placement is not allowed and will be reverted. Starts true unless the server already denied it. */
  public boolean canBuild() {
    return build;
  }

  public void setBuild(boolean build) {
    this.build = build;
  }
}
