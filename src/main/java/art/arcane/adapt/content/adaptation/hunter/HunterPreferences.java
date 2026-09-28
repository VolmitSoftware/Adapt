package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Color;
import org.bukkit.Material;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HunterPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> TRAIL = CommonPreferences.toggle("private-trail", key("trail", "Private blood trail"));
  static final PlayerPreference<CommonPreferences.Toggle> BLOCK_DROPS = CommonPreferences.toggle("block-drops", key("blocks", "Collect block drops"));
  static final PlayerPreference<CommonPreferences.Toggle> MOB_DROPS = CommonPreferences.toggle("mob-drops", key("mobs", "Collect mob drops"));
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("hunger-reserve", key("reserve", "Minimum hunger to activate"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Material.BOWL),
          choice(Reserve.FIVE, "five", "At least 5 hunger", Material.APPLE),
          choice(Reserve.TEN, "ten", "At least 10 hunger", Material.BREAD),
          choice(Reserve.FIFTEEN, "fifteen", "At least 15 hunger", Material.COOKED_BEEF))));
  static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", key("items", "Collected items"), Items.ALL, List.of(
          choice(Items.ALL, "all", "All permitted", Material.CHEST),
          choice(Items.FOOD, "food", "Food only", Material.COOKED_BEEF),
          choice(Items.BLOCKS, "blocks_only", "Blocks only", Material.STONE),
          choice(Items.MATERIALS, "materials", "Crafting drops only", Material.LEATHER))));
  static final PlayerPreference<Glow> GLOW = new PlayerPreference<>(Glow.class,
      new PlayerPreference.Definition<>("trail-color", key("glow", "Blood trail color"), Glow.BLOOD, List.of(
          choice(Glow.BLOOD, "blood", "Blood red", Material.RED_DYE),
          choice(Glow.WHITE, "white", "White", Material.WHITE_DYE),
          choice(Glow.CYAN, "cyan", "Cyan", Material.CYAN_DYE),
          choice(Glow.GOLD, "gold", "Gold", Material.YELLOW_DYE))));

  private HunterPreferences() {}

  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(TRAIL, BLOCK_DROPS, MOB_DROPS, SNEAK, RESERVE, ITEMS, GLOW)) {
      if (added.add(preference.label().id())) { builder.add(preference.label()); }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("hunter.preferences.") && added.add(choice.label().id())) { builder.add(choice.label()); }
      }
    }
  }
  private static TextKey key(String id, String text) { return TextKey.of("hunter.preferences." + id, text); }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Reserve {
    NONE(0), FIVE(5), TEN(10), FIFTEEN(15);
    private final int minimum;
    Reserve(int minimum) { this.minimum = minimum; }
    boolean permits(int food) { return food >= minimum; }
  }
  enum Items {
    ALL, FOOD, BLOCKS, MATERIALS;
    boolean accepts(Material material) {
      return switch (this) {
        case ALL -> true;
        case FOOD -> material.isEdible();
        case BLOCKS -> material.isBlock();
        case MATERIALS -> material == Material.LEATHER || material == Material.FEATHER || material == Material.BONE
            || material == Material.STRING || material == Material.GUNPOWDER || material == Material.SPIDER_EYE
            || material == Material.SLIME_BALL || material == Material.ENDER_PEARL || material == Material.BLAZE_ROD;
      };
    }
  }
  enum Glow {
    BLOOD, WHITE, CYAN, GOLD;
    Color color() {
      return switch (this) {
        case BLOOD -> Color.fromRGB(150, 10, 10);
        case WHITE -> Color.WHITE;
        case CYAN -> Color.AQUA;
        case GOLD -> Color.YELLOW;
      };
    }
  }
}
