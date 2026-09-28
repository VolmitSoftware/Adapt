package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.generator.structure.StructureType;

import java.util.List;

final class DiscoveryPreferences {
  private DiscoveryPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, TextKey label) {
    return new PlayerPreference<>(Reserve.class, new PlayerPreference.Definition<>(id, label, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, DiscoveryMessages.RESERVE_NONE, Material.GLASS_BOTTLE, 1),
        new PlayerPreference.Choice<>(Reserve.FIVE, DiscoveryMessages.RESERVE_FIVE, Material.EXPERIENCE_BOTTLE, 1),
        new PlayerPreference.Choice<>(Reserve.TEN, DiscoveryMessages.RESERVE_TEN, Material.LAPIS_LAZULI, 1))));
  }

  static PlayerPreference<Palette> palette(String id, TextKey label) {
    return new PlayerPreference<>(Palette.class, new PlayerPreference.Definition<>(id, label, Palette.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Palette.DEFAULT, DiscoveryMessages.PALETTE_DEFAULT, Material.WHITE_DYE, 1),
        new PlayerPreference.Choice<>(Palette.AQUA, DiscoveryMessages.PALETTE_AQUA, Material.CYAN_DYE, 1),
        new PlayerPreference.Choice<>(Palette.GOLD, DiscoveryMessages.PALETTE_GOLD, Material.YELLOW_DYE, 1),
        new PlayerPreference.Choice<>(Palette.PURPLE, DiscoveryMessages.PALETTE_PURPLE, Material.PURPLE_DYE, 1))));
  }

  static PlayerPreference<Structures> structures(String id, TextKey label) {
    return new PlayerPreference<>(Structures.class, new PlayerPreference.Definition<>(id, label, Structures.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Structures.DEFAULT, DiscoveryMessages.STRUCTURE_DEFAULT, Material.MAP, 1),
        new PlayerPreference.Choice<>(Structures.SETTLEMENTS, DiscoveryMessages.STRUCTURE_SETTLEMENTS, Material.BELL, 1),
        new PlayerPreference.Choice<>(Structures.MINESHAFT, DiscoveryMessages.STRUCTURE_MINESHAFT, Material.RAIL, 1),
        new PlayerPreference.Choice<>(Structures.MONUMENT, DiscoveryMessages.STRUCTURE_MONUMENT, Material.PRISMARINE, 1),
        new PlayerPreference.Choice<>(Structures.STRONGHOLD, DiscoveryMessages.STRUCTURE_STRONGHOLD, Material.ENDER_EYE, 1),
        new PlayerPreference.Choice<>(Structures.FORTRESS, DiscoveryMessages.STRUCTURE_FORTRESS, Material.NETHER_BRICKS, 1),
        new PlayerPreference.Choice<>(Structures.END_CITY, DiscoveryMessages.STRUCTURE_END_CITY, Material.PURPUR_BLOCK, 1))));
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

  enum Structures {
    DEFAULT, SETTLEMENTS, MINESHAFT, MONUMENT, STRONGHOLD, FORTRESS, END_CITY;

    StructureType type() {
      return switch (this) {
        case DEFAULT -> null;
        case SETTLEMENTS -> StructureType.JIGSAW;
        case MINESHAFT -> StructureType.MINESHAFT;
        case MONUMENT -> StructureType.OCEAN_MONUMENT;
        case STRONGHOLD -> StructureType.STRONGHOLD;
        case FORTRESS -> StructureType.FORTRESS;
        case END_CITY -> StructureType.END_CITY;
      };
    }
  }

  enum Reserve {
    NONE(0), FIVE(5), TEN(10);

    private final int amount;

    Reserve(int amount) {
      this.amount = amount;
    }

    int amount() {
      return amount;
    }
  }
}
