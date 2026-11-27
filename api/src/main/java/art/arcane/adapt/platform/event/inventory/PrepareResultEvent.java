package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * An anvil, smithing table, grindstone or other result menu computed its result. inputs() are the
 * input slots in order. Mutable: result (null or empty clears it).
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The player viewing the menu. */
  public PlayerRef player() {
    return player;
  }

  /** Kind of the result menu. */
  public InventoryKind kind() {
    return kind;
  }

  /** Read-only input slots in order: two for an anvil or grindstone, three for a smithing table. */
  public List<ItemRef> inputs() {
    return inputs;
  }

  /** The result about to be shown, or null when there is none. */
  public ItemRef result() {
    return result;
  }

  /** Replaces the result; null or an empty item clears it. */
  public void setResult(ItemRef result) {
    this.result = result;
  }
}
