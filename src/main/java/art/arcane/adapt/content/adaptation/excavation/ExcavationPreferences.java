package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.List;

final class ExcavationPreferences {
  private ExcavationPreferences() {
  }

  static PlayerPreference<Palette> palette(String id, TextKey label) {
    return new PlayerPreference<>(Palette.class, new PlayerPreference.Definition<>(id, label, Palette.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Palette.DEFAULT, ExcavationMessages.PREFERENCE_PALETTE_DEFAULT, Material.WHITE_DYE, 1),
        new PlayerPreference.Choice<>(Palette.AQUA, ExcavationMessages.PREFERENCE_PALETTE_AQUA, Material.CYAN_DYE, 1),
        new PlayerPreference.Choice<>(Palette.GOLD, ExcavationMessages.PREFERENCE_PALETTE_GOLD, Material.YELLOW_DYE, 1),
        new PlayerPreference.Choice<>(Palette.PURPLE, ExcavationMessages.PREFERENCE_PALETTE_PURPLE, Material.PURPLE_DYE, 1))));
  }

  static PlayerPreference<Trigger> trigger(String id, TextKey label) {
    return new PlayerPreference<>(Trigger.class, new PlayerPreference.Definition<>(id, label, Trigger.SNEAK, List.of(
        new PlayerPreference.Choice<>(Trigger.SNEAK, ExcavationMessages.PREFERENCE_TRIGGER_SNEAK, Material.LEATHER_BOOTS, 1),
        new PlayerPreference.Choice<>(Trigger.SUPPRESS, ExcavationMessages.PREFERENCE_TRIGGER_SUPPRESS, Material.FEATHER, 1),
        new PlayerPreference.Choice<>(Trigger.ALWAYS, ExcavationMessages.PREFERENCE_TRIGGER_ALWAYS, Material.IRON_SHOVEL, 1))));
  }

  static PlayerPreference<Ores> ores(String id, TextKey label) {
    return new PlayerPreference<>(Ores.class, new PlayerPreference.Definition<>(id, label, Ores.ALL, List.of(
        new PlayerPreference.Choice<>(Ores.ALL, ExcavationMessages.PREFERENCE_ORES_ALL, Material.STONE, 1),
        new PlayerPreference.Choice<>(Ores.COAL, ExcavationMessages.PREFERENCE_ORES_COAL, Material.COAL, 1),
        new PlayerPreference.Choice<>(Ores.IRON, ExcavationMessages.PREFERENCE_ORES_IRON, Material.IRON_INGOT, 1),
        new PlayerPreference.Choice<>(Ores.COPPER, ExcavationMessages.PREFERENCE_ORES_COPPER, Material.COPPER_INGOT, 1),
        new PlayerPreference.Choice<>(Ores.GOLD, ExcavationMessages.PREFERENCE_ORES_GOLD, Material.GOLD_INGOT, 1),
        new PlayerPreference.Choice<>(Ores.DIAMOND, ExcavationMessages.PREFERENCE_ORES_DIAMOND, Material.DIAMOND, 1),
        new PlayerPreference.Choice<>(Ores.EMERALD, ExcavationMessages.PREFERENCE_ORES_EMERALD, Material.EMERALD, 1),
        new PlayerPreference.Choice<>(Ores.REDSTONE, ExcavationMessages.PREFERENCE_ORES_REDSTONE, Material.REDSTONE, 1),
        new PlayerPreference.Choice<>(Ores.LAPIS, ExcavationMessages.PREFERENCE_ORES_LAPIS, Material.LAPIS_LAZULI, 1),
        new PlayerPreference.Choice<>(Ores.QUARTZ, ExcavationMessages.PREFERENCE_ORES_QUARTZ, Material.QUARTZ, 1),
        new PlayerPreference.Choice<>(Ores.NETHERITE, ExcavationMessages.PREFERENCE_ORES_NETHERITE, Material.NETHERITE_INGOT, 1))));
  }

  static PlayerPreference<Materials> materials(String id, TextKey label) {
    return new PlayerPreference<>(Materials.class, new PlayerPreference.Definition<>(id, label, Materials.ALL, List.of(
        new PlayerPreference.Choice<>(Materials.ALL, ExcavationMessages.PREFERENCE_MATERIALS_ALL, Material.CHEST, 1),
        new PlayerPreference.Choice<>(Materials.SOIL, ExcavationMessages.PREFERENCE_MATERIALS_SOIL, Material.DIRT, 1),
        new PlayerPreference.Choice<>(Materials.SAND, ExcavationMessages.PREFERENCE_MATERIALS_SAND, Material.SAND, 1),
        new PlayerPreference.Choice<>(Materials.SNOW, ExcavationMessages.PREFERENCE_MATERIALS_SNOW, Material.SNOW_BLOCK, 1),
        new PlayerPreference.Choice<>(Materials.CLAY, ExcavationMessages.PREFERENCE_MATERIALS_CLAY, Material.CLAY, 1),
        new PlayerPreference.Choice<>(Materials.GRAVEL, ExcavationMessages.PREFERENCE_MATERIALS_GRAVEL, Material.GRAVEL, 1),
        new PlayerPreference.Choice<>(Materials.FLINT, ExcavationMessages.PREFERENCE_MATERIALS_FLINT, Material.FLINT, 1))));
  }

  enum Palette {
    DEFAULT, AQUA, GOLD, PURPLE;

    Color color(Color fallback) {
      return switch (this) {
        case DEFAULT -> fallback;
        case AQUA -> Color.AQUA;
        case GOLD -> Color.YELLOW;
        case PURPLE -> Color.PURPLE;
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

    boolean allows(Material material) {
      return this == ALL || (this == NETHERITE ? material == Material.ANCIENT_DEBRIS : material.name().contains(name()));
    }
  }

  enum Materials {
    ALL, SOIL, SAND, SNOW, CLAY, GRAVEL, FLINT;

    boolean allows(Material material) {
      return switch (this) {
        case ALL -> true;
        case SOIL -> material.name().contains("DIRT") || material == Material.GRASS_BLOCK || material == Material.PODZOL || material.name().contains("MUD");
        case SAND -> material == Material.SAND || material == Material.RED_SAND;
        case SNOW -> material.name().contains("SNOW");
        case CLAY -> material == Material.CLAY || material == Material.CLAY_BALL;
        case GRAVEL -> material == Material.GRAVEL;
        case FLINT -> material == Material.FLINT;
      };
    }
  }
}
