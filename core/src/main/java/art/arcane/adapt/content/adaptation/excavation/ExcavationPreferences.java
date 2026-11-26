package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

final class ExcavationPreferences {
  private static final Id ANCIENT_DEBRIS = Id.minecraft("ancient_debris");

  private ExcavationPreferences() {
  }

  static PlayerPreference<Palette> palette(String id, TextKey label) {
    return new PlayerPreference<>(Palette.class, new PlayerPreference.Definition<>(id, label, Palette.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Palette.DEFAULT, ExcavationMessages.PREFERENCE_PALETTE_DEFAULT, Id.minecraft("white_dye"), 1),
        new PlayerPreference.Choice<>(Palette.AQUA, ExcavationMessages.PREFERENCE_PALETTE_AQUA, Id.minecraft("cyan_dye"), 1),
        new PlayerPreference.Choice<>(Palette.GOLD, ExcavationMessages.PREFERENCE_PALETTE_GOLD, Id.minecraft("yellow_dye"), 1),
        new PlayerPreference.Choice<>(Palette.PURPLE, ExcavationMessages.PREFERENCE_PALETTE_PURPLE, Id.minecraft("purple_dye"), 1))));
  }

  static PlayerPreference<Trigger> trigger(String id, TextKey label) {
    return trigger(id, label, Trigger.SNEAK);
  }

  static PlayerPreference<Trigger> trigger(String id, TextKey label, Trigger fallback) {
    return new PlayerPreference<>(Trigger.class, new PlayerPreference.Definition<>(id, label, fallback, List.of(
        new PlayerPreference.Choice<>(Trigger.SNEAK, ExcavationMessages.PREFERENCE_TRIGGER_SNEAK, Id.minecraft("leather_boots"), 1),
        new PlayerPreference.Choice<>(Trigger.SUPPRESS, ExcavationMessages.PREFERENCE_TRIGGER_SUPPRESS, Id.minecraft("feather"), 1),
        new PlayerPreference.Choice<>(Trigger.ALWAYS, ExcavationMessages.PREFERENCE_TRIGGER_ALWAYS, Id.minecraft("iron_shovel"), 1))));
  }

  static PlayerPreference<Ores> ores(String id, TextKey label) {
    return new PlayerPreference<>(Ores.class, new PlayerPreference.Definition<>(id, label, Ores.ALL, List.of(
        new PlayerPreference.Choice<>(Ores.ALL, ExcavationMessages.PREFERENCE_ORES_ALL, Id.minecraft("stone"), 1),
        new PlayerPreference.Choice<>(Ores.COAL, ExcavationMessages.PREFERENCE_ORES_COAL, Id.minecraft("coal"), 1),
        new PlayerPreference.Choice<>(Ores.IRON, ExcavationMessages.PREFERENCE_ORES_IRON, Id.minecraft("iron_ingot"), 1),
        new PlayerPreference.Choice<>(Ores.COPPER, ExcavationMessages.PREFERENCE_ORES_COPPER, Id.minecraft("copper_ingot"), 1),
        new PlayerPreference.Choice<>(Ores.GOLD, ExcavationMessages.PREFERENCE_ORES_GOLD, Id.minecraft("gold_ingot"), 1),
        new PlayerPreference.Choice<>(Ores.DIAMOND, ExcavationMessages.PREFERENCE_ORES_DIAMOND, Id.minecraft("diamond"), 1),
        new PlayerPreference.Choice<>(Ores.EMERALD, ExcavationMessages.PREFERENCE_ORES_EMERALD, Id.minecraft("emerald"), 1),
        new PlayerPreference.Choice<>(Ores.REDSTONE, ExcavationMessages.PREFERENCE_ORES_REDSTONE, Id.minecraft("redstone"), 1),
        new PlayerPreference.Choice<>(Ores.LAPIS, ExcavationMessages.PREFERENCE_ORES_LAPIS, Id.minecraft("lapis_lazuli"), 1),
        new PlayerPreference.Choice<>(Ores.QUARTZ, ExcavationMessages.PREFERENCE_ORES_QUARTZ, Id.minecraft("quartz"), 1),
        new PlayerPreference.Choice<>(Ores.NETHERITE, ExcavationMessages.PREFERENCE_ORES_NETHERITE, Id.minecraft("netherite_ingot"), 1))));
  }

  static PlayerPreference<Materials> materials(String id, TextKey label) {
    return new PlayerPreference<>(Materials.class, new PlayerPreference.Definition<>(id, label, Materials.ALL, List.of(
        new PlayerPreference.Choice<>(Materials.ALL, ExcavationMessages.PREFERENCE_MATERIALS_ALL, Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Materials.SOIL, ExcavationMessages.PREFERENCE_MATERIALS_SOIL, Id.minecraft("dirt"), 1),
        new PlayerPreference.Choice<>(Materials.SAND, ExcavationMessages.PREFERENCE_MATERIALS_SAND, Id.minecraft("sand"), 1),
        new PlayerPreference.Choice<>(Materials.SNOW, ExcavationMessages.PREFERENCE_MATERIALS_SNOW, Id.minecraft("snow_block"), 1),
        new PlayerPreference.Choice<>(Materials.CLAY, ExcavationMessages.PREFERENCE_MATERIALS_CLAY, Id.minecraft("clay"), 1),
        new PlayerPreference.Choice<>(Materials.GRAVEL, ExcavationMessages.PREFERENCE_MATERIALS_GRAVEL, Id.minecraft("gravel"), 1),
        new PlayerPreference.Choice<>(Materials.FLINT, ExcavationMessages.PREFERENCE_MATERIALS_FLINT, Id.minecraft("flint"), 1))));
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
