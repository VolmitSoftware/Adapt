package art.arcane.adapt.bukkit.platform.item;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.compat.PaperApi;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

/** The parts of {@link BukkitItemRef} bound to item components added after Minecraft 1.21.1. */
final class BukkitItemVersion {
  private BukkitItemVersion() {
  }

  static String effectiveName(ItemStack stack) {
    return PaperApi.effectiveName(stack);
  }

  static Id itemModel(ItemMeta meta) {
    return meta.hasItemModel() ? BukkitConversions.id(meta.getItemModel()) : null;
  }

  static void setItemModel(ItemMeta meta, NamespacedKey model) {
    meta.setItemModel(model);
  }

  static Component customName(PotionMeta meta) {
    return PaperApi.potionName(meta);
  }

  static void setCustomName(PotionMeta meta, Component name) {
    PaperApi.setPotionName(meta, name);
  }
}
