package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HunterPreferences {
  public static final PlayerPreference<CommonPreferences.Toggle> TRAIL = CommonPreferences.toggle("private-trail", key("trail", "Private blood trail"));
  static final PlayerPreference<CommonPreferences.Toggle> BLOCK_DROPS = CommonPreferences.toggle("block-drops", key("blocks", "Collect block drops"));
  static final PlayerPreference<CommonPreferences.Toggle> MOB_DROPS = CommonPreferences.toggle("mob-drops", key("mobs", "Collect mob drops"));
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("hunger-reserve", key("reserve", "Minimum hunger to activate"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Id.minecraft("bowl")),
          choice(Reserve.FIVE, "five", "At least 5 hunger", Id.minecraft("apple")),
          choice(Reserve.TEN, "ten", "At least 10 hunger", Id.minecraft("bread")),
          choice(Reserve.FIFTEEN, "fifteen", "At least 15 hunger", Id.minecraft("cooked_beef")))));
  static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", key("items", "Collected items"), Items.ALL, List.of(
          choice(Items.ALL, "all", "All permitted", Id.minecraft("chest")),
          choice(Items.FOOD, "food", "Food only", Id.minecraft("cooked_beef")),
          choice(Items.BLOCKS, "blocks_only", "Blocks only", Id.minecraft("stone")),
          choice(Items.MATERIALS, "materials", "Crafting drops only", Id.minecraft("leather")))));
  public static final PlayerPreference<Glow> GLOW = new PlayerPreference<>(Glow.class,
      new PlayerPreference.Definition<>("trail-color", key("glow", "Blood trail color"), Glow.BLOOD, List.of(
          choice(Glow.BLOOD, "blood", "Blood red", Id.minecraft("red_dye")),
          choice(Glow.WHITE, "white", "White", Id.minecraft("white_dye")),
          choice(Glow.CYAN, "cyan", "Cyan", Id.minecraft("cyan_dye")),
          choice(Glow.GOLD, "gold", "Gold", Id.minecraft("yellow_dye")))));

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
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  public enum Reserve {
    NONE(0), FIVE(5), TEN(10), FIFTEEN(15);
    private final int minimum;
    Reserve(int minimum) { this.minimum = minimum; }
    boolean permits(int food) { return food >= minimum; }
  }
  enum Items {
    ALL, FOOD, BLOCKS, MATERIALS;
    private static final Set<Id> MATERIAL_DROPS = Set.of(Id.minecraft("leather"), Id.minecraft("feather"),
        Id.minecraft("bone"), Id.minecraft("string"), Id.minecraft("gunpowder"), Id.minecraft("spider_eye"),
        Id.minecraft("slime_ball"), Id.minecraft("ender_pearl"), Id.minecraft("blaze_rod"));

    boolean accepts(Id material) {
      return switch (this) {
        case ALL -> true;
        case FOOD -> AdaptCore.platform().host().items().isEdible(material);
        case BLOCKS -> AdaptCore.platform().host().items().isBlock(material);
        case MATERIALS -> MATERIAL_DROPS.contains(material);
      };
    }
  }
  public enum Glow {
    BLOOD, WHITE, CYAN, GOLD;
    Rgb color() {
      return switch (this) {
        case BLOOD -> new Rgb(150, 10, 10);
        case WHITE -> new Rgb(255, 255, 255);
        case CYAN -> new Rgb(0, 255, 255);
        case GOLD -> new Rgb(255, 255, 0);
      };
    }
  }
}
