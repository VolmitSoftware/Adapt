package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Locale;

public final class PickaxePreferences {
  static final PlayerPreference<CommonPreferences.Toggle> BYPASS = CommonPreferences.toggle("sneak-bypass", key("bypass"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> REQUIRE_SNEAK = CommonPreferences.toggle("require-sneak", key("require_sneak"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> NOTICE = CommonPreferences.toggle("durability-notice", key("notice"), CommonPreferences.Toggle.OFF);
  static final Text LOW_DURABILITY = key("low_durability");
  static final PlayerPreference<Trigger> TRIGGER = PlayerPreference.of("trigger", Trigger.class, Trigger.SNEAK, List.of(
          choice(Trigger.SNEAK, "sneak", Id.minecraft("leather_boots")),
          choice(Trigger.NOT_SNEAKING, "not_sneaking", Id.minecraft("iron_boots")),
          choice(Trigger.ALWAYS, "always", Id.minecraft("diamond_boots")))).labelled(key("trigger"));
  static final PlayerPreference<Materials> MATERIALS = PlayerPreference.of("materials", Materials.class, Materials.ALL, List.of(
          choice(Materials.ALL, "all", Id.minecraft("chest")),
          choice(Materials.ORES, "ores", Id.minecraft("iron_ore")),
          choice(Materials.IRON, "iron", Id.minecraft("iron_ingot")),
          choice(Materials.GOLD, "gold", Id.minecraft("gold_ingot")),
          choice(Materials.COPPER, "copper", Id.minecraft("copper_ingot")),
          choice(Materials.GEMS, "gems", Id.minecraft("diamond")),
          choice(Materials.STONE, "stone", Id.minecraft("stone")))).labelled(key("materials"));
  static final PlayerPreference<Reserve> RESERVE = PlayerPreference.of("durability-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "no_reserve", Id.minecraft("wooden_pickaxe")),
          choice(Reserve.TENTH, "tenth", Id.minecraft("stone_pickaxe")),
          choice(Reserve.QUARTER, "quarter", Id.minecraft("iron_pickaxe")),
          choice(Reserve.HALF, "half", Id.minecraft("diamond_pickaxe")))).labelled(key("reserve"));
  static final PlayerPreference<Size> SIZE = PlayerPreference.of("tunnel-size", Size.class, Size.FULL, List.of(
          choice(Size.FULL, "full", Id.minecraft("diamond_pickaxe")),
          choice(Size.THREE_BY_TWO, "three_by_two", Id.minecraft("iron_pickaxe")),
          choice(Size.ONE_BY_TWO, "one_by_two", Id.minecraft("stone_pickaxe")))).labelled(key("size"));
  static final PlayerPreference<Glow> GLOW = PlayerPreference.of("glow-color", Glow.class, Glow.ORE, List.of(
          choice(Glow.ORE, "ore_color", Id.minecraft("redstone")),
          choice(Glow.WHITE, "white", Id.minecraft("white_dye")),
          choice(Glow.CYAN, "cyan", Id.minecraft("cyan_dye")),
          choice(Glow.GOLD, "gold_color", Id.minecraft("yellow_dye")))).labelled(key("glow"));

  private PickaxePreferences() {
  }


  private static Text key(String id) {
    return Text.key("adapt.pickaxe.preferences." + id);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
  }

  enum Trigger {
    SNEAK, NOT_SNEAKING, ALWAYS;

    boolean accepts(boolean sneaking) {
      return this == ALWAYS || (this == SNEAK) == sneaking;
    }
  }

  enum Materials {
    ALL, ORES, IRON, GOLD, COPPER, GEMS, STONE;

    boolean accepts(Id material) {
      String name = material.path().toUpperCase(Locale.ROOT);
      return switch (this) {
        case ALL -> true;
        case ORES -> name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT")
            || is(material, "coal") || is(material, "diamond") || is(material, "emerald")
            || is(material, "redstone") || is(material, "lapis_lazuli") || is(material, "quartz")
            || is(material, "ancient_debris");
        case IRON -> name.contains("IRON") && (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT"));
        case GOLD -> name.contains("GOLD") && (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT"));
        case COPPER -> name.contains("COPPER") && (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT"));
        case GEMS -> is(material, "diamond") || is(material, "emerald")
            || (name.endsWith("_ORE") && (name.contains("DIAMOND") || name.contains("EMERALD")));
        case STONE -> is(material, "stone") || is(material, "cobblestone") || is(material, "deepslate")
            || is(material, "cobbled_deepslate");
      };
    }

    private static boolean is(Id material, String path) {
      return material.isMinecraft() && material.path().equals(path);
    }
  }

  enum Reserve {
    NONE(0D), TENTH(0.1D), QUARTER(0.25D), HALF(0.5D);
    private final double fraction;

    Reserve(double fraction) {
      this.fraction = fraction;
    }

    boolean permits(ItemRef item, int cost) {
      if (this == NONE) {
        return true;
      }
      if (item == null || item.isEmpty()) {
        return false;
      }
      int maximum = item.maxDamage();
      return maximum > 0 && maximum - item.damage() - cost >= Math.ceil(maximum * fraction);
    }
  }

  enum Size {
    FULL, THREE_BY_TWO, ONE_BY_TWO;
    int width(int learned) {
      return this == ONE_BY_TWO ? Math.min(1, learned) : this == THREE_BY_TWO ? Math.min(3, learned) : learned;
    }
    int height(int learned) {
      return this == FULL ? learned : Math.min(2, learned);
    }
  }

  enum Glow {
    ORE, WHITE, CYAN, GOLD;
    Rgb color(Rgb ore) {
      return switch (this) {
        case ORE -> ore;
        case WHITE -> new Rgb(255, 255, 255);
        case CYAN -> new Rgb(0, 255, 255);
        case GOLD -> new Rgb(255, 255, 0);
      };
    }
  }
}
