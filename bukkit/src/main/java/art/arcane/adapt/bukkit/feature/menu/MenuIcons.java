package art.arcane.adapt.bukkit.feature.menu;

import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.item.BukkitItemRef;
import art.arcane.adapt.platform.menu.MenuIcon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class MenuIcons {
  private MenuIcons() {
  }

  static ItemStack stack(MenuIcon icon) {
    if (icon.item() != null) {
      ItemStack prepared = BukkitRefs.item(icon.item());
      if (prepared != null && !prepared.getType().isAir()) {
        return prepared.clone();
      }
    }
    Material material = BukkitRefs.material(icon.type());
    if (material == null || material.isAir() || !material.isItem()) {
      material = Material.BARRIER;
    }
    ItemStack stack = new ItemStack(material, icon.amount());
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return stack;
    }
    if (icon.name() != null) {
      meta.displayName(BukkitText.itemText(icon.name()));
    }
    if (!icon.lore().isEmpty()) {
      List<Component> lines = new ArrayList<>(icon.lore().size());
      for (String line : icon.lore()) {
        lines.add(BukkitText.itemText(line));
      }
      meta.lore(lines);
    }
    stack.setItemMeta(meta);
    BukkitItemRef item = new BukkitItemRef(stack);
    if (icon.glint()) {
      item.setGlintOverride(Boolean.TRUE);
    }
    if (icon.itemModel() != null) {
      item.setItemModel(icon.itemModel());
    }
    return stack;
  }
}
