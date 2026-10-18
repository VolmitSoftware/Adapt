package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

final class SorterVariants {
  private static final String NONE = "";
  private static final String SEPARATOR = ",";

  private SorterVariants() {
  }

  static String of(ItemStack item) {
    if (!SorterRules.precise(item.getType()) || !item.hasItemMeta()) {
      return NONE;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta stored) {
      List<String> keys = new ArrayList<>(stored.getStoredEnchants().size());
      for (Enchantment enchantment : stored.getStoredEnchants().keySet()) {
        keys.add(enchantment.getKey().toString());
      }
      keys.sort(null);
      return String.join(SEPARATOR, keys);
    }
    if (meta instanceof PotionMeta potion) {
      PotionType type = potion.getBasePotionType();
      return type == null ? NONE : type.getKey().toString();
    }
    return NONE;
  }
}
