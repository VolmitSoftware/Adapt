package art.arcane.adapt.bukkit.feature.pack;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Component text through Paper's API: item names and lore with their shadows, and chest titles with their fonts. Paper only. */
final class PaperPackText {
  private PaperPackText() {
  }

  static void name(ItemMeta meta, Component name) {
    meta.displayName(name);
  }

  static void lore(ItemMeta meta, List<Component> lines) {
    meta.lore(lines);
  }

  static Inventory chest(InventoryHolder holder, int size, Component title) {
    return Bukkit.createInventory(holder, size, title);
  }
}
