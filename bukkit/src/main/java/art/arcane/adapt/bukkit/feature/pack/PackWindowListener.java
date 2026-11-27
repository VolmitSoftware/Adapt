package art.arcane.adapt.bukkit.feature.pack;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Bukkit events for pack windows. Chest windows: every click on the chest is cancelled and routed, plain clicks in a
 * free player inventory are left alone. Canvas windows: a click another plugin cancelled is let through to the canvas,
 * which never moves an item, so protection plugins cannot swallow menu clicks.
 */
final class PackWindowListener implements Listener {
  private static final Set<InventoryAction> FREE_INVENTORY_ACTIONS = EnumSet.of(
      InventoryAction.PICKUP_ALL,
      InventoryAction.PICKUP_SOME,
      InventoryAction.PICKUP_HALF,
      InventoryAction.PICKUP_ONE,
      InventoryAction.PLACE_ALL,
      InventoryAction.PLACE_SOME,
      InventoryAction.PLACE_ONE,
      InventoryAction.SWAP_WITH_CURSOR);

  private final BukkitPackWindows windows;

  PackWindowListener(BukkitPackWindows windows) {
    this.windows = windows;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChestClick(InventoryClickEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof ChestPackWindow window) || !window.ownedBy(windows)) {
      return;
    }
    int raw = event.getRawSlot();
    boolean chest = raw >= 0 && raw < event.getView().getTopInventory().getSize();
    if (!chest && window.freePlayerInventory() && event.getClickedInventory() != null
        && FREE_INVENTORY_ACTIONS.contains(event.getAction())) {
      return;
    }
    event.setCancelled(true);
    if (chest) {
      window.clicked(raw, PackClicks.kind(event.getClick()));
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChestDrag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof ChestPackWindow window && window.ownedBy(windows)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChestClose(InventoryCloseEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof ChestPackWindow window && window.ownedBy(windows)) {
      window.closedByViewer();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onCanvasClick(InventoryClickEvent event) {
    if (event.isCancelled() && windows.canvasView(event.getWhoClicked().getUniqueId(), event.getView())) {
      event.setCancelled(false);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    windows.forget(event.getPlayer().getUniqueId());
  }
}
