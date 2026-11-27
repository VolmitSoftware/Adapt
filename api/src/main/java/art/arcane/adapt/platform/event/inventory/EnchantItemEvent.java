package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A player picks an enchanting table offer. Mutable: enchantments to add, level cost, cancelled (no enchant). */
@ApiStatus.AvailableSince("1.0.0")
public class EnchantItemEvent extends PlayerEvent {
  private final BlockRef table;
  private final ItemRef item;
  private final int button;
  private final Map<Id, Integer> enchantsToAdd;
  private int expLevelCost;

  public EnchantItemEvent(PlayerRef enchanter, BlockRef table, ItemRef item, int button, Map<Id, Integer> enchantsToAdd,
                          int expLevelCost) {
    super(enchanter);
    this.table = Objects.requireNonNull(table, "table");
    this.item = Objects.requireNonNull(item, "item");
    this.button = button;
    this.enchantsToAdd = new LinkedHashMap<>(enchantsToAdd);
    this.expLevelCost = expLevelCost;
  }

  /** The enchanting table. */
  public BlockRef table() {
    return table;
  }

  /** The item being enchanted. */
  public ItemRef item() {
    return item;
  }

  /** Offer button, 0 to 2. */
  public int button() {
    return button;
  }

  /** The live map of enchantment id to level the item receives. Edit it to change what is applied. */
  public Map<Id, Integer> enchantsToAdd() {
    return enchantsToAdd;
  }

  /** Experience level cost of the offer. */
  public int expLevelCost() {
    return expLevelCost;
  }

  /** Changes the level cost. Negative values become 0. */
  public void setExpLevelCost(int cost) {
    this.expLevelCost = Math.max(0, cost);
  }
}
