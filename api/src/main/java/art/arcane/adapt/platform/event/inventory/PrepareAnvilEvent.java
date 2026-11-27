package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * An anvil computed its result. Handlers of PrepareResultEvent receive it too. Mutable: result,
 * repairCost.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PrepareAnvilEvent extends PrepareResultEvent {
  private final String renameText;
  private int repairCost;

  public PrepareAnvilEvent(PlayerRef player, InventoryKind kind, List<ItemRef> inputs, ItemRef result, String renameText, int repairCost) {
    super(player, kind, inputs, result);
    this.renameText = renameText;
    this.repairCost = repairCost;
  }

  /** Text in the rename field, as the platform reports it. */
  public String renameText() {
    return renameText;
  }

  /** Experience level cost of the result. */
  public int repairCost() {
    return repairCost;
  }

  /** Changes the level cost shown and charged. */
  public void setRepairCost(int repairCost) {
    this.repairCost = repairCost;
  }
}
