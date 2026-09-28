package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PickaxePreferences {
  static final PlayerPreference<CommonPreferences.Toggle> BYPASS = CommonPreferences.toggle("sneak-bypass", key("bypass", "Sneak to bypass"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> REQUIRE_SNEAK = CommonPreferences.toggle("require-sneak", key("require_sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> NOTICE = CommonPreferences.toggle("durability-notice", key("notice", "Low durability notice"), CommonPreferences.Toggle.OFF);
  static final TextKey LOW_DURABILITY = key("low_durability", "Your pickaxe has less than 10% durability remaining.");
  static final PlayerPreference<Trigger> TRIGGER = new PlayerPreference<>(Trigger.class,
      new PlayerPreference.Definition<>("trigger", key("trigger", "Activation gesture"), Trigger.SNEAK, List.of(
          choice(Trigger.SNEAK, "sneak", "While sneaking", Material.LEATHER_BOOTS),
          choice(Trigger.NOT_SNEAKING, "not_sneaking", "While not sneaking", Material.IRON_BOOTS),
          choice(Trigger.ALWAYS, "always", "Always", Material.DIAMOND_BOOTS))));
  static final PlayerPreference<Materials> MATERIALS = new PlayerPreference<>(Materials.class,
      new PlayerPreference.Definition<>("materials", key("materials", "Eligible materials"), Materials.ALL, List.of(
          choice(Materials.ALL, "all", "All permitted", Material.CHEST),
          choice(Materials.ORES, "ores", "Ores and raw minerals", Material.IRON_ORE),
          choice(Materials.IRON, "iron", "Iron only", Material.IRON_INGOT),
          choice(Materials.GOLD, "gold", "Gold only", Material.GOLD_INGOT),
          choice(Materials.COPPER, "copper", "Copper only", Material.COPPER_INGOT),
          choice(Materials.GEMS, "gems", "Diamond and emerald", Material.DIAMOND),
          choice(Materials.STONE, "stone", "Stone and deepslate", Material.STONE))));
  static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("durability-reserve", key("reserve", "Durability remaining after use"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "no_reserve", "No extra reserve", Material.WOODEN_PICKAXE),
          choice(Reserve.TENTH, "tenth", "Keep 10%", Material.STONE_PICKAXE),
          choice(Reserve.QUARTER, "quarter", "Keep 25%", Material.IRON_PICKAXE),
          choice(Reserve.HALF, "half", "Keep 50%", Material.DIAMOND_PICKAXE))));
  static final PlayerPreference<Size> SIZE = new PlayerPreference<>(Size.class,
      new PlayerPreference.Definition<>("tunnel-size", key("size", "Tunnel size limit"), Size.FULL, List.of(
          choice(Size.FULL, "full", "Full learned size", Material.DIAMOND_PICKAXE),
          choice(Size.THREE_BY_TWO, "three_by_two", "At most 3 by 2", Material.IRON_PICKAXE),
          choice(Size.ONE_BY_TWO, "one_by_two", "At most 1 by 2", Material.STONE_PICKAXE))));
  static final PlayerPreference<Glow> GLOW = new PlayerPreference<>(Glow.class,
      new PlayerPreference.Definition<>("glow-color", key("glow", "Ore outline color"), Glow.ORE, List.of(
          choice(Glow.ORE, "ore_color", "Match ore", Material.REDSTONE),
          choice(Glow.WHITE, "white", "White", Material.WHITE_DYE),
          choice(Glow.CYAN, "cyan", "Cyan", Material.CYAN_DYE),
          choice(Glow.GOLD, "gold_color", "Gold", Material.YELLOW_DYE))));

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

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Material icon) {
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

    boolean accepts(Material material) {
      String name = material.name();
      return switch (this) {
        case ALL -> true;
        case ORES -> name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT")
            || material == Material.COAL || material == Material.DIAMOND || material == Material.EMERALD
            || material == Material.REDSTONE || material == Material.LAPIS_LAZULI || material == Material.QUARTZ
            || material == Material.ANCIENT_DEBRIS;
        case IRON -> name.contains("IRON") && (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT"));
        case GOLD -> name.contains("GOLD") && (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT"));
        case COPPER -> name.contains("COPPER") && (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT"));
        case GEMS -> material == Material.DIAMOND || material == Material.EMERALD
            || (name.endsWith("_ORE") && (name.contains("DIAMOND") || name.contains("EMERALD")));
        case STONE -> material == Material.STONE || material == Material.COBBLESTONE || material == Material.DEEPSLATE
            || material == Material.COBBLED_DEEPSLATE;
      };
    }
  }

  enum Reserve {
    NONE(0D), TENTH(0.1D), QUARTER(0.25D), HALF(0.5D);
    private final double fraction;

    Reserve(double fraction) {
      this.fraction = fraction;
    }

    boolean permits(ItemStack item, int cost) {
      if (this == NONE) {
        return true;
      }
      if (!(item.getItemMeta() instanceof Damageable damage)) {
        return false;
      }
      int maximum = damage.hasMaxDamage() ? damage.getMaxDamage() : item.getType().getMaxDurability();
      return maximum > 0 && maximum - damage.getDamage() - cost >= Math.ceil(maximum * fraction);
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
    Color color(Color ore) {
      return switch (this) {
        case ORE -> ore;
        case WHITE -> Color.WHITE;
        case CYAN -> Color.AQUA;
        case GOLD -> Color.YELLOW;
      };
    }
  }
}
