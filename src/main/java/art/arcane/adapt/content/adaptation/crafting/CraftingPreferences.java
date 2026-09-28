package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.List;

final class CraftingPreferences {
  private CraftingPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, TextKey label) {
    return new PlayerPreference<>(Reserve.class, new PlayerPreference.Definition<>(id, label, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, CraftingMessages.RESERVE_NONE, Material.GLASS_BOTTLE, 1),
        new PlayerPreference.Choice<>(Reserve.EIGHT, CraftingMessages.RESERVE_EIGHT, Material.EXPERIENCE_BOTTLE, 1),
        new PlayerPreference.Choice<>(Reserve.THIRTY_TWO, CraftingMessages.RESERVE_THIRTY_TWO, Material.LAPIS_LAZULI, 1))));
  }

  static PlayerPreference<Category> categories(String id, TextKey label) {
    return new PlayerPreference<>(Category.class, new PlayerPreference.Definition<>(id, label, Category.ALL, List.of(
        new PlayerPreference.Choice<>(Category.ALL, CraftingMessages.CATEGORY_ALL, Material.CHEST, 1),
        new PlayerPreference.Choice<>(Category.BLOCKS, CraftingMessages.CATEGORY_BLOCKS, Material.STONE, 1),
        new PlayerPreference.Choice<>(Category.EQUIPMENT, CraftingMessages.CATEGORY_EQUIPMENT, Material.IRON_PICKAXE, 1),
        new PlayerPreference.Choice<>(Category.FOOD, CraftingMessages.CATEGORY_FOOD, Material.BREAD, 1),
        new PlayerPreference.Choice<>(Category.MINERALS, CraftingMessages.CATEGORY_MINERALS, Material.IRON_INGOT, 1))));
  }

  static PlayerPreference<Storage> storage(String id, TextKey label) {
    return new PlayerPreference<>(Storage.class, new PlayerPreference.Definition<>(id, label, Storage.SERVER, List.of(
        new PlayerPreference.Choice<>(Storage.SERVER, CraftingMessages.STORAGE_SERVER, Material.CHEST, 1),
        new PlayerPreference.Choice<>(Storage.SLOTS, CraftingMessages.STORAGE_SLOTS, Material.BARREL, 1),
        new PlayerPreference.Choice<>(Storage.BUNDLE, CraftingMessages.STORAGE_BUNDLE, Material.BUNDLE, 1))));
  }

  enum Storage {
    SERVER, SLOTS, BUNDLE
  }

  enum Category {
    ALL, BLOCKS, EQUIPMENT, FOOD, MINERALS;

    boolean allows(Material material) {
      return switch (this) {
        case ALL -> true;
        case BLOCKS -> material.isBlock();
        case EQUIPMENT -> material.getMaxDurability() > 0;
        case FOOD -> material.isEdible();
        case MINERALS -> material.name().endsWith("_INGOT") || material.name().startsWith("RAW_")
            || material == Material.DIAMOND || material == Material.EMERALD || material == Material.REDSTONE
            || material == Material.LAPIS_LAZULI || material == Material.COAL || material == Material.QUARTZ;
      };
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
