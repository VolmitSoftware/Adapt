package art.arcane.adapt.bukkit.feature.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Lets a viewer move items in their own inventory under a menu that allows it: plain clicks there are un-cancelled
 * at HIGH, whoever cancelled them before. Register it after the skills so Adapt's own HIGH click handlers run first.
 */
public final class MenuCursor implements Listener {
  @EventHandler(priority = EventPriority.HIGH)
  public void on(InventoryClickEvent event) {
    Inventory top = event.getView().getTopInventory();
    Inventory clicked = event.getClickedInventory();
    if (clicked == null || clicked == top || !MenuWindow.allowsFreeInventoryAction(event.getAction())) {
      return;
    }
    if (MenuWindow.freeInventoryOpen(event.getWhoClicked().getUniqueId())) {
      event.setCancelled(false);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    MenuWindow.forget(event.getPlayer().getUniqueId());
  }
}
