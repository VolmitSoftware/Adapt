package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

final class CraftingPreferences {
  private CraftingPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, Text label) {
    return PlayerPreference.of(id, Reserve.class, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, Text.key("adapt.crafting.preferences.reserve_none"), Id.minecraft("glass_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.EIGHT, Text.key("adapt.crafting.preferences.reserve_eight"), Id.minecraft("experience_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.THIRTY_TWO, Text.key("adapt.crafting.preferences.reserve_thirty_two"), Id.minecraft("lapis_lazuli"), 1))).labelled(label);
  }

  static PlayerPreference<Category> categories(String id, Text label) {
    return PlayerPreference.of(id, Category.class, Category.ALL, List.of(
        new PlayerPreference.Choice<>(Category.ALL, Text.key("adapt.crafting.preferences.category_all"), Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Category.BLOCKS, Text.key("adapt.crafting.preferences.category_blocks"), Id.minecraft("stone"), 1),
        new PlayerPreference.Choice<>(Category.EQUIPMENT, Text.key("adapt.crafting.preferences.category_equipment"), Id.minecraft("iron_pickaxe"), 1),
        new PlayerPreference.Choice<>(Category.FOOD, Text.key("adapt.crafting.preferences.category_food"), Id.minecraft("bread"), 1),
        new PlayerPreference.Choice<>(Category.MINERALS, Text.key("adapt.crafting.preferences.category_minerals"), Id.minecraft("iron_ingot"), 1))).labelled(label);
  }

  static PlayerPreference<Storage> storage(String id, Text label) {
    return PlayerPreference.of(id, Storage.class, Storage.SERVER, List.of(
        new PlayerPreference.Choice<>(Storage.SERVER, Text.key("adapt.crafting.preferences.storage_server"), Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Storage.SLOTS, Text.key("adapt.crafting.preferences.storage_slots"), Id.minecraft("barrel"), 1),
        new PlayerPreference.Choice<>(Storage.BUNDLE, Text.key("adapt.crafting.preferences.storage_bundle"), Id.minecraft("bundle"), 1))).labelled(label);
  }

  enum Storage {
    SERVER, SLOTS, BUNDLE
  }

  enum Category {
    ALL, BLOCKS, EQUIPMENT, FOOD, MINERALS;

    boolean allows(Id material) {
      return switch (this) {
        case ALL -> true;
        case BLOCKS -> items().isBlock(material);
        case EQUIPMENT -> items().maxDurability(material) > 0;
        case FOOD -> items().isEdible(material);
        case MINERALS -> isMineral(material);
      };
    }

    private static boolean isMineral(Id material) {
      if (!material.isMinecraft()) {
        return false;
      }
      String name = material.path();
      return name.endsWith("_ingot") || name.startsWith("raw_")
          || name.equals("diamond") || name.equals("emerald") || name.equals("redstone")
          || name.equals("lapis_lazuli") || name.equals("coal") || name.equals("quartz");
    }

    private static ItemOps items() {
      return AdaptApi.platform().host().items();
    }
  }

  enum Reserve {
    NONE(0), EIGHT(8), THIRTY_TWO(32);

    private final int amount;

    Reserve(int amount) {
      this.amount = amount;
    }

    int amount() {
      return amount;
    }
  }
}
