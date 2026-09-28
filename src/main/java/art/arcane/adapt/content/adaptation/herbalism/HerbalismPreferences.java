package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.List;

final class HerbalismPreferences {
  private HerbalismPreferences() {
  }

  static PlayerPreference<Materials> materials(String id, TextKey label) {
    return new PlayerPreference<>(Materials.class, new PlayerPreference.Definition<>(id, label, Materials.ALL, List.of(
        new PlayerPreference.Choice<>(Materials.ALL, HerbalismMessages.PREFERENCE_MATERIAL_ALL, Material.CHEST, 1),
        new PlayerPreference.Choice<>(Materials.WHEAT, HerbalismMessages.PREFERENCE_MATERIAL_WHEAT, Material.WHEAT, 1),
        new PlayerPreference.Choice<>(Materials.CARROT, HerbalismMessages.PREFERENCE_MATERIAL_CARROT, Material.CARROT, 1),
        new PlayerPreference.Choice<>(Materials.POTATO, HerbalismMessages.PREFERENCE_MATERIAL_POTATO, Material.POTATO, 1),
        new PlayerPreference.Choice<>(Materials.BEETROOT, HerbalismMessages.PREFERENCE_MATERIAL_BEETROOT, Material.BEETROOT, 1),
        new PlayerPreference.Choice<>(Materials.MELON, HerbalismMessages.PREFERENCE_MATERIAL_MELON, Material.MELON_SLICE, 1),
        new PlayerPreference.Choice<>(Materials.PUMPKIN, HerbalismMessages.PREFERENCE_MATERIAL_PUMPKIN, Material.PUMPKIN, 1),
        new PlayerPreference.Choice<>(Materials.NETHER, HerbalismMessages.PREFERENCE_MATERIAL_NETHER, Material.NETHER_WART, 1),
        new PlayerPreference.Choice<>(Materials.FLOWERS, HerbalismMessages.PREFERENCE_MATERIAL_FLOWERS, Material.DANDELION, 1),
        new PlayerPreference.Choice<>(Materials.FUNGI, HerbalismMessages.PREFERENCE_MATERIAL_FUNGI, Material.RED_MUSHROOM, 1),
        new PlayerPreference.Choice<>(Materials.LEAVES, HerbalismMessages.PREFERENCE_MATERIAL_LEAVES, Material.OAK_LEAVES, 1),
        new PlayerPreference.Choice<>(Materials.BERRIES, HerbalismMessages.PREFERENCE_MATERIAL_BERRIES, Material.SWEET_BERRIES, 1),
        new PlayerPreference.Choice<>(Materials.KELP, HerbalismMessages.PREFERENCE_MATERIAL_KELP, Material.KELP, 1))));
  }

  static boolean isPlantingItem(Material material) {
    return material.name().endsWith("_SEEDS") || material == Material.CARROT || material == Material.POTATO
        || material == Material.NETHER_WART || material.name().endsWith("_SAPLING") || material == Material.PITCHER_POD;
  }

  enum Materials {
    ALL, WHEAT, CARROT, POTATO, BEETROOT, MELON, PUMPKIN, NETHER, FLOWERS, FUNGI, LEAVES, BERRIES, KELP;

    boolean allows(Material material) {
      return switch (this) {
        case ALL -> true;
        case NETHER -> material == Material.NETHER_WART || material == Material.NETHER_WART_BLOCK;
        case FLOWERS -> Tag.FLOWERS.isTagged(material) || material.name().contains("FLOWER") || material.name().contains("PITCHER");
        case FUNGI -> material.name().contains("MUSHROOM") || material.name().contains("FUNGUS");
        case LEAVES -> Tag.LEAVES.isTagged(material);
        case BERRIES -> material.name().contains("BERR") || material.name().contains("CAVE_VINE");
        case KELP -> material.name().contains("KELP") || material.name().contains("SEAGRASS");
        default -> material.name().contains(name());
      };
    }
  }
}
