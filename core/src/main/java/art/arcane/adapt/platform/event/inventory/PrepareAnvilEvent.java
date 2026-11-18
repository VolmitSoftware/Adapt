package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;

/**
 * An anvil computed its result. Handlers of PrepareResultEvent receive it too. Mutable: result,
 * repairCost.
 */
public class PrepareAnvilEvent extends PrepareResultEvent {
  private final String renameText;
  private int repairCost;

  public PrepareAnvilEvent(PlayerRef player, InventoryKind kind, List<ItemRef> inputs, ItemRef result, String renameText, int repairCost) {
    super(player, kind, inputs, result);
    this.renameText = renameText;
    this.repairCost = repairCost;
  }

  public String renameText() {
    return renameText;
  }

  public int repairCost() {
    return repairCost;
  }

  public void setRepairCost(int repairCost) {
    this.repairCost = repairCost;
  }
}
