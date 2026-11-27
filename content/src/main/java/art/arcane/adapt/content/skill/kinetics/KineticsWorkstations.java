package art.arcane.adapt.content.skill.kinetics;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;

public final class KineticsWorkstations {
  private KineticsWorkstations() {
  }

  public static double anvilXp(int levelsBefore, int levelsAfter, double xpPerLevel) {
    int spent = levelsBefore - levelsAfter;
    if (spent <= 0 || !Double.isFinite(xpPerLevel) || xpPerLevel <= 0D) {
      return 0D;
    }
    return spent * xpPerLevel;
  }

  public static double grindstoneXp(int removedEnchantLevels, double xpPerEnchantLevel) {
    if (removedEnchantLevels <= 0 || !Double.isFinite(xpPerEnchantLevel) || xpPerEnchantLevel <= 0D) {
      return 0D;
    }
    return removedEnchantLevels * xpPerEnchantLevel;
  }

  public static int removedEnchantLevels(ItemRef first, ItemRef second, ItemRef result) {
    return Math.max(0, enchantLevels(first) + enchantLevels(second) - enchantLevels(result));
  }

  static int enchantLevels(ItemRef item) {
    if (item == null || item.isEmpty() || item.type().isAir()) {
      return 0;
    }
    return sumWithoutCurses(item.enchantments()) + sumWithoutCurses(item.storedEnchantments());
  }

  private static int sumWithoutCurses(Map<Id, Integer> enchantments) {
    int total = 0;
    for (Map.Entry<Id, Integer> entry : enchantments.entrySet()) {
      if (!entry.getKey().path().endsWith("_curse") && entry.getValue() != null && entry.getValue() > 0) {
        total += entry.getValue();
      }
    }
    return total;
  }
}
