package art.arcane.adapt.api.golem;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class GolemToolEnchants {
  private GolemToolEnchants() {
  }

  public static int unbreaking(ItemStack tool) {
    return tool.getEnchantmentLevel(Enchantment.UNBREAKING);
  }

  public static int silkTouch(ItemStack tool) {
    return tool.getEnchantmentLevel(Enchantment.SILK_TOUCH);
  }

  public static int fortune(ItemStack tool) {
    return tool.getEnchantmentLevel(Enchantment.FORTUNE);
  }

  public static int luckOfTheSea(ItemStack tool) {
    return tool.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
  }

  public static int lure(ItemStack tool) {
    return tool.getEnchantmentLevel(Enchantment.LURE);
  }
}
