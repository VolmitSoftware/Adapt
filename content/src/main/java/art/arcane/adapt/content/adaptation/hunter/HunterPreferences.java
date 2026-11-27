package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import java.util.List;
import java.util.Set;

public final class HunterPreferences {
  public static final PlayerPreference<CommonPreferences.Toggle> TRAIL = CommonPreferences.toggle("private-trail", key("trail"));
  static final PlayerPreference<CommonPreferences.Toggle> BLOCK_DROPS = CommonPreferences.toggle("block-drops", key("blocks"));
  static final PlayerPreference<CommonPreferences.Toggle> MOB_DROPS = CommonPreferences.toggle("mob-drops", key("mobs"));
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<Reserve> RESERVE = PlayerPreference.of("hunger-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("bowl")),
          choice(Reserve.FIVE, "five", Id.minecraft("apple")),
          choice(Reserve.TEN, "ten", Id.minecraft("bread")),
          choice(Reserve.FIFTEEN, "fifteen", Id.minecraft("cooked_beef")))).labelled(key("reserve"));
  static final PlayerPreference<Items> ITEMS = PlayerPreference.of("items", Items.class, Items.ALL, List.of(
          choice(Items.ALL, "all", Id.minecraft("chest")),
          choice(Items.FOOD, "food", Id.minecraft("cooked_beef")),
          choice(Items.BLOCKS, "blocks_only", Id.minecraft("stone")),
          choice(Items.MATERIALS, "materials", Id.minecraft("leather")))).labelled(key("items"));
  public static final PlayerPreference<Glow> GLOW = PlayerPreference.of("trail-color", Glow.class, Glow.BLOOD, List.of(
          choice(Glow.BLOOD, "blood", Id.minecraft("red_dye")),
          choice(Glow.WHITE, "white", Id.minecraft("white_dye")),
          choice(Glow.CYAN, "cyan", Id.minecraft("cyan_dye")),
          choice(Glow.GOLD, "gold", Id.minecraft("yellow_dye")))).labelled(key("glow"));

  private HunterPreferences() {}

  private static Text key(String id) {
    return Text.key("adapt.hunter.preferences." + id);
  }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
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
        case FOOD -> AdaptApi.platform().host().items().isEdible(material);
        case BLOCKS -> AdaptApi.platform().host().items().isBlock(material);
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
