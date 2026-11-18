package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PickaxePreferences {
  static final PlayerPreference<CommonPreferences.Toggle> BYPASS = CommonPreferences.toggle("sneak-bypass", key("bypass", "Sneak to bypass"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> REQUIRE_SNEAK = CommonPreferences.toggle("require-sneak", key("require_sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> NOTICE = CommonPreferences.toggle("durability-notice", key("notice", "Low durability notice"), CommonPreferences.Toggle.OFF);
  static final TextKey LOW_DURABILITY = key("low_durability", "Your pickaxe has less than 10% durability remaining.");
  static final PlayerPreference<Trigger> TRIGGER = new PlayerPreference<>(Trigger.class,
      new PlayerPreference.Definition<>("trigger", key("trigger", "Activation gesture"), Trigger.SNEAK, List.of(
          choice(Trigger.SNEAK, "sneak", "While sneaking", Id.minecraft("leather_boots")),
          choice(Trigger.NOT_SNEAKING, "not_sneaking", "While not sneaking", Id.minecraft("iron_boots")),
          choice(Trigger.ALWAYS, "always", "Always", Id.minecraft("diamond_boots")))));
  static final PlayerPreference<Materials> MATERIALS = new PlayerPreference<>(Materials.class,
      new PlayerPreference.Definition<>("materials", key("materials", "Eligible materials"), Materials.ALL, List.of(
          choice(Materials.ALL, "all", "All permitted", Id.minecraft("chest")),
          choice(Materials.ORES, "ores", "Ores and raw minerals", Id.minecraft("iron_ore")),
          choice(Materials.IRON, "iron", "Iron only", Id.minecraft("iron_ingot")),
          choice(Materials.GOLD, "gold", "Gold only", Id.minecraft("gold_ingot")),
          choice(Materials.COPPER, "copper", "Copper only", Id.minecraft("copper_ingot")),
          choice(Materials.GEMS, "gems", "Diamond and emerald", Id.minecraft("diamond")),
          choice(Materials.STONE, "stone", "Stone and deepslate", Id.minecraft("stone")))));
  static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("durability-reserve", key("reserve", "Durability remaining after use"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "no_reserve", "No extra reserve", Id.minecraft("wooden_pickaxe")),
          choice(Reserve.TENTH, "tenth", "Keep 10%", Id.minecraft("stone_pickaxe")),
          choice(Reserve.QUARTER, "quarter", "Keep 25%", Id.minecraft("iron_pickaxe")),
          choice(Reserve.HALF, "half", "Keep 50%", Id.minecraft("diamond_pickaxe")))));
  static final PlayerPreference<Size> SIZE = new PlayerPreference<>(Size.class,
      new PlayerPreference.Definition<>("tunnel-size", key("size", "Tunnel size limit"), Size.FULL, List.of(
          choice(Size.FULL, "full", "Full learned size", Id.minecraft("diamond_pickaxe")),
          choice(Size.THREE_BY_TWO, "three_by_two", "At most 3 by 2", Id.minecraft("iron_pickaxe")),
          choice(Size.ONE_BY_TWO, "one_by_two", "At most 1 by 2", Id.minecraft("stone_pickaxe")))));
  static final PlayerPreference<Glow> GLOW = new PlayerPreference<>(Glow.class,
      new PlayerPreference.Definition<>("glow-color", key("glow", "Ore outline color"), Glow.ORE, List.of(
          choice(Glow.ORE, "ore_color", "Match ore", Id.minecraft("redstone")),
          choice(Glow.WHITE, "white", "White", Id.minecraft("white_dye")),
          choice(Glow.CYAN, "cyan", "Cyan", Id.minecraft("cyan_dye")),
          choice(Glow.GOLD, "gold_color", "Gold", Id.minecraft("yellow_dye")))));

  private PickaxePreferences() {
  }

  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    builder.add(LOW_DURABILITY);
    for (PlayerPreference<?> preference : List.of(BYPASS, REQUIRE_SNEAK, NOTICE, TRIGGER, MATERIALS, RESERVE, SIZE, GLOW)) {
      if (added.add(preference.label().id())) {
        builder.add(preference.label());
      }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("pickaxe.preferences.") && added.add(choice.label().id())) {
          builder.add(choice.label());
        }
      }
    }
  }

  private static TextKey key(String id, String text) {
    return TextKey.of("pickaxe.preferences." + id, text);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
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
