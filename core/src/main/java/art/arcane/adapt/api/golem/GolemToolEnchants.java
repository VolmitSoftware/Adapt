package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

public final class GolemToolEnchants {
  private static final Id UNBREAKING = Id.minecraft("unbreaking");
  private static final Id SILK_TOUCH = Id.minecraft("silk_touch");
  private static final Id FORTUNE = Id.minecraft("fortune");
  private static final Id LUCK_OF_THE_SEA = Id.minecraft("luck_of_the_sea");
  private static final Id LURE = Id.minecraft("lure");

  private GolemToolEnchants() {
  }

  public static int unbreaking(ItemRef tool) {
    return tool.enchantmentLevel(UNBREAKING);
  }

  public static int silkTouch(ItemRef tool) {
    return tool.enchantmentLevel(SILK_TOUCH);
  }

  public static int fortune(ItemRef tool) {
    return tool.enchantmentLevel(FORTUNE);
  }

  public static int luckOfTheSea(ItemRef tool) {
    return tool.enchantmentLevel(LUCK_OF_THE_SEA);
  }

  public static int lure(ItemRef tool) {
    return tool.enchantmentLevel(LURE);
  }
}
