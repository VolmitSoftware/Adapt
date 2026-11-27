package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * A player harvests a block without breaking it (sweet berries, glow berries). Cancelling leaves the block
 * unharvested. The item list is read-only. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerHarvestBlockEvent extends PlayerEvent {
  private final BlockRef harvestedBlock;
  private final Hand hand;
  private final List<ItemRef> items;

  public PlayerHarvestBlockEvent(PlayerRef player, BlockRef harvestedBlock, Hand hand, List<ItemRef> items) {
    super(player);
    this.harvestedBlock = Objects.requireNonNull(harvestedBlock, "harvestedBlock");
    this.hand = Objects.requireNonNull(hand, "hand");
    this.items = List.copyOf(items);
  }

  /** The block harvested. */
  public BlockRef harvestedBlock() {
    return harvestedBlock;
  }

  /** The hand used; the main hand when the platform did not report one. */
  public Hand hand() {
    return hand;
  }

  /** Items the harvest drops, as an immutable copy. */
  public List<ItemRef> items() {
    return items;
  }
}
