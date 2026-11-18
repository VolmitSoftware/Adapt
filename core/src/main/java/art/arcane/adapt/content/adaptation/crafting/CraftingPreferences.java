package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.item.ItemOps;

import java.util.List;

final class CraftingPreferences {
  private CraftingPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, TextKey label) {
    return new PlayerPreference<>(Reserve.class, new PlayerPreference.Definition<>(id, label, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, CraftingMessages.RESERVE_NONE, Id.minecraft("glass_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.EIGHT, CraftingMessages.RESERVE_EIGHT, Id.minecraft("experience_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.THIRTY_TWO, CraftingMessages.RESERVE_THIRTY_TWO, Id.minecraft("lapis_lazuli"), 1))));
  }

  static PlayerPreference<Category> categories(String id, TextKey label) {
    return new PlayerPreference<>(Category.class, new PlayerPreference.Definition<>(id, label, Category.ALL, List.of(
        new PlayerPreference.Choice<>(Category.ALL, CraftingMessages.CATEGORY_ALL, Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Category.BLOCKS, CraftingMessages.CATEGORY_BLOCKS, Id.minecraft("stone"), 1),
        new PlayerPreference.Choice<>(Category.EQUIPMENT, CraftingMessages.CATEGORY_EQUIPMENT, Id.minecraft("iron_pickaxe"), 1),
        new PlayerPreference.Choice<>(Category.FOOD, CraftingMessages.CATEGORY_FOOD, Id.minecraft("bread"), 1),
        new PlayerPreference.Choice<>(Category.MINERALS, CraftingMessages.CATEGORY_MINERALS, Id.minecraft("iron_ingot"), 1))));
  }

  static PlayerPreference<Storage> storage(String id, TextKey label) {
    return new PlayerPreference<>(Storage.class, new PlayerPreference.Definition<>(id, label, Storage.SERVER, List.of(
        new PlayerPreference.Choice<>(Storage.SERVER, CraftingMessages.STORAGE_SERVER, Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Storage.SLOTS, CraftingMessages.STORAGE_SLOTS, Id.minecraft("barrel"), 1),
        new PlayerPreference.Choice<>(Storage.BUNDLE, CraftingMessages.STORAGE_BUNDLE, Id.minecraft("bundle"), 1))));
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
      return AdaptCore.platform().host().items();
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
