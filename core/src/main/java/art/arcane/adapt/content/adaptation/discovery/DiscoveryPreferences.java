package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

final class DiscoveryPreferences {
  private DiscoveryPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, TextKey label) {
    return new PlayerPreference<>(Reserve.class, new PlayerPreference.Definition<>(id, label, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, DiscoveryMessages.RESERVE_NONE, Id.minecraft("glass_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.FIVE, DiscoveryMessages.RESERVE_FIVE, Id.minecraft("experience_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.TEN, DiscoveryMessages.RESERVE_TEN, Id.minecraft("lapis_lazuli"), 1))));
  }

  static PlayerPreference<Palette> palette(String id, TextKey label) {
    return new PlayerPreference<>(Palette.class, new PlayerPreference.Definition<>(id, label, Palette.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Palette.DEFAULT, DiscoveryMessages.PALETTE_DEFAULT, Id.minecraft("white_dye"), 1),
        new PlayerPreference.Choice<>(Palette.AQUA, DiscoveryMessages.PALETTE_AQUA, Id.minecraft("cyan_dye"), 1),
        new PlayerPreference.Choice<>(Palette.GOLD, DiscoveryMessages.PALETTE_GOLD, Id.minecraft("yellow_dye"), 1),
        new PlayerPreference.Choice<>(Palette.PURPLE, DiscoveryMessages.PALETTE_PURPLE, Id.minecraft("purple_dye"), 1))));
  }

  static PlayerPreference<Structures> structures(String id, TextKey label) {
    return new PlayerPreference<>(Structures.class, new PlayerPreference.Definition<>(id, label, Structures.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Structures.DEFAULT, DiscoveryMessages.STRUCTURE_DEFAULT, Id.minecraft("map"), 1),
        new PlayerPreference.Choice<>(Structures.SETTLEMENTS, DiscoveryMessages.STRUCTURE_SETTLEMENTS, Id.minecraft("bell"), 1),
        new PlayerPreference.Choice<>(Structures.MINESHAFT, DiscoveryMessages.STRUCTURE_MINESHAFT, Id.minecraft("rail"), 1),
        new PlayerPreference.Choice<>(Structures.MONUMENT, DiscoveryMessages.STRUCTURE_MONUMENT, Id.minecraft("prismarine"), 1),
        new PlayerPreference.Choice<>(Structures.STRONGHOLD, DiscoveryMessages.STRUCTURE_STRONGHOLD, Id.minecraft("ender_eye"), 1),
        new PlayerPreference.Choice<>(Structures.FORTRESS, DiscoveryMessages.STRUCTURE_FORTRESS, Id.minecraft("nether_bricks"), 1),
        new PlayerPreference.Choice<>(Structures.END_CITY, DiscoveryMessages.STRUCTURE_END_CITY, Id.minecraft("purpur_block"), 1))));
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

  enum Structures {
    DEFAULT, SETTLEMENTS, MINESHAFT, MONUMENT, STRONGHOLD, FORTRESS, END_CITY;

    Id type() {
      return switch (this) {
        case DEFAULT -> null;
        case SETTLEMENTS -> DiscoveryStructures.JIGSAW;
        case MINESHAFT -> DiscoveryStructures.MINESHAFT;
        case MONUMENT -> DiscoveryStructures.OCEAN_MONUMENT;
        case STRONGHOLD -> DiscoveryStructures.STRONGHOLD;
        case FORTRESS -> DiscoveryStructures.FORTRESS;
        case END_CITY -> DiscoveryStructures.END_CITY;
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
