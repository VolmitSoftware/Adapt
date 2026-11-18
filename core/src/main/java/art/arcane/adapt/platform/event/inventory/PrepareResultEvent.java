package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;
import java.util.Objects;

/**
 * An anvil, smithing table, grindstone or other result menu computed its result. inputs() are the
 * input slots in order. Mutable: result (null or empty clears it).
 */
public class PrepareResultEvent extends AdaptEvent {
  private final PlayerRef player;
  private final InventoryKind kind;
  private final List<ItemRef> inputs;
  private ItemRef result;

  public PrepareResultEvent(PlayerRef player, InventoryKind kind, List<ItemRef> inputs, ItemRef result) {
    this.player = Objects.requireNonNull(player, "player");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.inputs = List.copyOf(inputs);
    this.result = result;
  }

  public PlayerRef player() {
    return player;
  }

  public InventoryKind kind() {
    return kind;
  }

  public List<ItemRef> inputs() {
    return inputs;
  }

  public ItemRef result() {
    return result;
  }

  public void setResult(ItemRef result) {
    this.result = result;
  }
}
