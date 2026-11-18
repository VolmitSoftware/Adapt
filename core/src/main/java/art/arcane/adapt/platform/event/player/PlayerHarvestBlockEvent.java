package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Objects;

/**
 * A player harvests a block without breaking it (sweet berries, glow berries). The item list is
 * read-only. Mutable: cancelled.
 */
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

  public BlockRef harvestedBlock() {
    return harvestedBlock;
  }

  public Hand hand() {
    return hand;
  }

  public List<ItemRef> items() {
    return items;
  }
}
