package art.arcane.adapt.bukkit.platform.item;

import art.arcane.adapt.platform.value.Id;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

/**
 * The parts of {@link BukkitItemRef} bound to item components added after Minecraft 1.21.1. Item models do not exist,
 * and a potion's name is its display name.
 */
final class BukkitItemVersion {
  private BukkitItemVersion() {
  }

  @SuppressWarnings("deprecation")
  static String effectiveName(ItemStack stack) {
    return stack.getI18NDisplayName();
  }

  static Id itemModel(ItemMeta meta) {
    return null;
  }

  static void setItemModel(ItemMeta meta, NamespacedKey model) {
  }

  static Component customName(PotionMeta meta) {
    return meta.displayName();
  }

  static void setCustomName(PotionMeta meta, Component name) {
    meta.displayName(name);
  }
}
