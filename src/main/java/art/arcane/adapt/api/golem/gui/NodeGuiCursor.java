package art.arcane.adapt.api.golem.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.EnumSet;
import java.util.Set;

public final class NodeGuiCursor implements Listener {
  private static final Set<InventoryAction> BOTTOM_ACTIONS = EnumSet.of(
      InventoryAction.PICKUP_ALL,
      InventoryAction.PICKUP_SOME,
      InventoryAction.PICKUP_HALF,
      InventoryAction.PICKUP_ONE,
      InventoryAction.PLACE_ALL,
      InventoryAction.PLACE_SOME,
      InventoryAction.PLACE_ONE,
      InventoryAction.SWAP_WITH_CURSOR);

  public static boolean allowsBottomClick(InventoryAction action) {
    return BOTTOM_ACTIONS.contains(action);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    Inventory top = e.getView().getTopInventory();
    Inventory clicked = e.getClickedInventory();
    if (clicked == null || clicked == top || !allowsBottomClick(e.getAction())) {
      return;
    }
    if (NodeGui.isCursorWindow(e.getWhoClicked().getUniqueId(), top)) {
      e.setCancelled(false);
    }
  }
}
