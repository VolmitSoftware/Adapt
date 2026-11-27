package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;

final class DiscoveryPreferences {
  private DiscoveryPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, Text label) {
    return PlayerPreference.of(id, Reserve.class, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, Text.key("adapt.discovery.preferences.reserve_none"), Id.minecraft("glass_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.FIVE, Text.key("adapt.discovery.preferences.reserve_five"), Id.minecraft("experience_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.TEN, Text.key("adapt.discovery.preferences.reserve_ten"), Id.minecraft("lapis_lazuli"), 1))).labelled(label);
  }

  static PlayerPreference<Palette> palette(String id, Text label) {
    return PlayerPreference.of(id, Palette.class, Palette.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Palette.DEFAULT, Text.key("adapt.discovery.preferences.palette_default"), Id.minecraft("white_dye"), 1),
        new PlayerPreference.Choice<>(Palette.AQUA, Text.key("adapt.discovery.preferences.palette_aqua"), Id.minecraft("cyan_dye"), 1),
        new PlayerPreference.Choice<>(Palette.GOLD, Text.key("adapt.discovery.preferences.palette_gold"), Id.minecraft("yellow_dye"), 1),
        new PlayerPreference.Choice<>(Palette.PURPLE, Text.key("adapt.discovery.preferences.palette_purple"), Id.minecraft("purple_dye"), 1))).labelled(label);
  }

  static PlayerPreference<Structures> structures(String id, Text label) {
    return PlayerPreference.of(id, Structures.class, Structures.DEFAULT, List.of(
        new PlayerPreference.Choice<>(Structures.DEFAULT, Text.key("adapt.discovery.preferences.structure_default"), Id.minecraft("map"), 1),
        new PlayerPreference.Choice<>(Structures.SETTLEMENTS, Text.key("adapt.discovery.preferences.structure_settlements"), Id.minecraft("bell"), 1),
        new PlayerPreference.Choice<>(Structures.MINESHAFT, Text.key("adapt.discovery.preferences.structure_mineshaft"), Id.minecraft("rail"), 1),
        new PlayerPreference.Choice<>(Structures.MONUMENT, Text.key("adapt.discovery.preferences.structure_monument"), Id.minecraft("prismarine"), 1),
        new PlayerPreference.Choice<>(Structures.STRONGHOLD, Text.key("adapt.discovery.preferences.structure_stronghold"), Id.minecraft("ender_eye"), 1),
        new PlayerPreference.Choice<>(Structures.FORTRESS, Text.key("adapt.discovery.preferences.structure_fortress"), Id.minecraft("nether_bricks"), 1),
        new PlayerPreference.Choice<>(Structures.END_CITY, Text.key("adapt.discovery.preferences.structure_end_city"), Id.minecraft("purpur_block"), 1))).labelled(label);
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
