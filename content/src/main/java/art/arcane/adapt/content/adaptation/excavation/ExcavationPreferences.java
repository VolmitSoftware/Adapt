package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;

final class ExcavationPreferences {
  private static final Id ANCIENT_DEBRIS = Id.minecraft("ancient_debris");

  private ExcavationPreferences() {
  }

  static PlayerPreference<Palette> palette(String id, Text label) {
    return PlayerPreference.of(id, Palette.class, Palette.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Palette.DEFAULT, Text.key("adapt.excavation.preferences.palette.default"), Id.minecraft("white_dye"), 1),
        new PlayerPreference.Choice<>(Palette.AQUA, Text.key("adapt.excavation.preferences.palette.aqua"), Id.minecraft("cyan_dye"), 1),
        new PlayerPreference.Choice<>(Palette.GOLD, Text.key("adapt.excavation.preferences.palette.gold"), Id.minecraft("yellow_dye"), 1),
        new PlayerPreference.Choice<>(Palette.PURPLE, Text.key("adapt.excavation.preferences.palette.purple"), Id.minecraft("purple_dye"), 1))).labelled(label);
  }

  static PlayerPreference<Trigger> trigger(String id, Text label) {
    return trigger(id, label, Trigger.SNEAK);
  }

  static PlayerPreference<Trigger> trigger(String id, Text label, Trigger fallback) {
    return PlayerPreference.of(id, Trigger.class, fallback, List.of(
        new PlayerPreference.Choice<>(Trigger.SNEAK, Text.key("adapt.excavation.preferences.trigger.sneak"), Id.minecraft("leather_boots"), 1),
        new PlayerPreference.Choice<>(Trigger.SUPPRESS, Text.key("adapt.excavation.preferences.trigger.suppress"), Id.minecraft("feather"), 1),
        new PlayerPreference.Choice<>(Trigger.ALWAYS, Text.key("adapt.excavation.preferences.trigger.always"), Id.minecraft("iron_shovel"), 1))).labelled(label);
  }

  static PlayerPreference<Ores> ores(String id, Text label) {
    return PlayerPreference.of(id, Ores.class, Ores.ALL, List.of(
        new PlayerPreference.Choice<>(Ores.ALL, Text.key("adapt.excavation.preferences.ores.all"), Id.minecraft("stone"), 1),
        new PlayerPreference.Choice<>(Ores.COAL, Text.key("adapt.excavation.preferences.ores.coal"), Id.minecraft("coal"), 1),
        new PlayerPreference.Choice<>(Ores.IRON, Text.key("adapt.excavation.preferences.ores.iron"), Id.minecraft("iron_ingot"), 1),
        new PlayerPreference.Choice<>(Ores.COPPER, Text.key("adapt.excavation.preferences.ores.copper"), Id.minecraft("copper_ingot"), 1),
        new PlayerPreference.Choice<>(Ores.GOLD, Text.key("adapt.excavation.preferences.ores.gold"), Id.minecraft("gold_ingot"), 1),
        new PlayerPreference.Choice<>(Ores.DIAMOND, Text.key("adapt.excavation.preferences.ores.diamond"), Id.minecraft("diamond"), 1),
        new PlayerPreference.Choice<>(Ores.EMERALD, Text.key("adapt.excavation.preferences.ores.emerald"), Id.minecraft("emerald"), 1),
        new PlayerPreference.Choice<>(Ores.REDSTONE, Text.key("adapt.excavation.preferences.ores.redstone"), Id.minecraft("redstone"), 1),
        new PlayerPreference.Choice<>(Ores.LAPIS, Text.key("adapt.excavation.preferences.ores.lapis"), Id.minecraft("lapis_lazuli"), 1),
        new PlayerPreference.Choice<>(Ores.QUARTZ, Text.key("adapt.excavation.preferences.ores.quartz"), Id.minecraft("quartz"), 1),
        new PlayerPreference.Choice<>(Ores.NETHERITE, Text.key("adapt.excavation.preferences.ores.netherite"), Id.minecraft("netherite_ingot"), 1))).labelled(label);
  }

  static PlayerPreference<Materials> materials(String id, Text label) {
    return PlayerPreference.of(id, Materials.class, Materials.ALL, List.of(
        new PlayerPreference.Choice<>(Materials.ALL, Text.key("adapt.excavation.preferences.materials.all"), Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Materials.SOIL, Text.key("adapt.excavation.preferences.materials.soil"), Id.minecraft("dirt"), 1),
        new PlayerPreference.Choice<>(Materials.SAND, Text.key("adapt.excavation.preferences.materials.sand"), Id.minecraft("sand"), 1),
        new PlayerPreference.Choice<>(Materials.SNOW, Text.key("adapt.excavation.preferences.materials.snow"), Id.minecraft("snow_block"), 1),
        new PlayerPreference.Choice<>(Materials.CLAY, Text.key("adapt.excavation.preferences.materials.clay"), Id.minecraft("clay"), 1),
        new PlayerPreference.Choice<>(Materials.GRAVEL, Text.key("adapt.excavation.preferences.materials.gravel"), Id.minecraft("gravel"), 1),
        new PlayerPreference.Choice<>(Materials.FLINT, Text.key("adapt.excavation.preferences.materials.flint"), Id.minecraft("flint"), 1))).labelled(label);
  }

  enum Palette {
    DEFAULT, AQUA, GOLD, PURPLE;

    Rgb color(Rgb fallback) {
      return switch (this) {
        case DEFAULT -> fallback;
        case AQUA -> new Rgb(0, 255, 255);
        case GOLD -> new Rgb(255, 255, 0);
        case PURPLE -> new Rgb(128, 0, 128);
      };
    }
  }

  enum Trigger {
    SNEAK, SUPPRESS, ALWAYS;

    boolean allows(boolean sneaking) {
      return this == ALWAYS || (this == SNEAK) == sneaking;
    }
  }

  enum Ores {
    ALL, COAL, IRON, COPPER, GOLD, DIAMOND, EMERALD, REDSTONE, LAPIS, QUARTZ, NETHERITE;

    boolean allows(Id material) {
      return this == ALL || (this == NETHERITE ? material.equals(ANCIENT_DEBRIS) : material.legacyName().contains(name()));
    }
  }

  enum Materials {
    ALL, SOIL, SAND, SNOW, CLAY, GRAVEL, FLINT;

    boolean allows(Id material) {
      String name = material.legacyName();
      return switch (this) {
        case ALL -> true;
        case SOIL -> name.contains("DIRT") || is(material, "grass_block") || is(material, "podzol") || name.contains("MUD");
        case SAND -> is(material, "sand") || is(material, "red_sand");
        case SNOW -> name.contains("SNOW");
        case CLAY -> is(material, "clay") || is(material, "clay_ball");
        case GRAVEL -> is(material, "gravel");
        case FLINT -> is(material, "flint");
      };
    }

    private static boolean is(Id material, String vanillaPath) {
      return material.isMinecraft() && material.path().equals(vanillaPath);
    }
  }
}
