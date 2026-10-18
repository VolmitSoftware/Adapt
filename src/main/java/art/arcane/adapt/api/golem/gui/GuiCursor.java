package art.arcane.adapt.api.golem.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiCursor implements Listener {
  private static final Set<InventoryAction> BOTTOM_ACTIONS = EnumSet.of(
      InventoryAction.PICKUP_ALL,
      InventoryAction.PICKUP_SOME,
      InventoryAction.PICKUP_HALF,
      InventoryAction.PICKUP_ONE,
      InventoryAction.PLACE_ALL,
      InventoryAction.PLACE_SOME,
      InventoryAction.PLACE_ONE,
      InventoryAction.SWAP_WITH_CURSOR);
  private static final Map<UUID, Inventory> WINDOWS = new ConcurrentHashMap<>();

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
    if (isTracked(e.getWhoClicked().getUniqueId(), top)) {
      e.setCancelled(false);
    }
  }

  static boolean isTracked(UUID viewer, Inventory top) {
    Inventory tracked = WINDOWS.get(viewer);
    return tracked != null && tracked == top;
  }

  static void track(UUID viewer, Inventory top) {
    WINDOWS.put(viewer, Objects.requireNonNull(top));
  }

  static void untrack(UUID viewer, Inventory top) {
    WINDOWS.remove(viewer, top);
  }
}
