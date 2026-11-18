package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An enchanting table shows its three offers. Offer entries may be null; each non-null offer is
 * mutable. Mutable: offers, cancelled.
 */
public class PrepareItemEnchantEvent extends PlayerEvent {
  private final BlockRef table;
  private final ItemRef item;
  private final int bonus;
  private final List<EnchantOffer> offers;

  public PrepareItemEnchantEvent(PlayerRef player, BlockRef table, ItemRef item, int bonus, List<EnchantOffer> offers) {
    super(player);
    this.table = Objects.requireNonNull(table, "table");
    this.item = Objects.requireNonNull(item, "item");
    this.bonus = bonus;
    this.offers = Collections.unmodifiableList(new ArrayList<>(offers));
  }

  public BlockRef table() {
    return table;
  }

  public ItemRef item() {
    return item;
  }

  public int bonus() {
    return bonus;
  }

  public List<EnchantOffer> offers() {
    return offers;
  }
}
