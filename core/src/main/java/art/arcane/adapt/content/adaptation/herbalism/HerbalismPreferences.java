package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

final class HerbalismPreferences {
  private static final Id FLOWERS_TAG = Id.minecraft("flowers");
  private static final Id LEAVES_TAG = Id.minecraft("leaves");

  private HerbalismPreferences() {
  }

  static PlayerPreference<Materials> materials(String id, TextKey label) {
    return new PlayerPreference<>(Materials.class, new PlayerPreference.Definition<>(id, label, Materials.ALL, List.of(
        new PlayerPreference.Choice<>(Materials.ALL, HerbalismMessages.PREFERENCE_MATERIAL_ALL, Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Materials.WHEAT, HerbalismMessages.PREFERENCE_MATERIAL_WHEAT, Id.minecraft("wheat"), 1),
        new PlayerPreference.Choice<>(Materials.CARROT, HerbalismMessages.PREFERENCE_MATERIAL_CARROT, Id.minecraft("carrot"), 1),
        new PlayerPreference.Choice<>(Materials.POTATO, HerbalismMessages.PREFERENCE_MATERIAL_POTATO, Id.minecraft("potato"), 1),
        new PlayerPreference.Choice<>(Materials.BEETROOT, HerbalismMessages.PREFERENCE_MATERIAL_BEETROOT, Id.minecraft("beetroot"), 1),
        new PlayerPreference.Choice<>(Materials.MELON, HerbalismMessages.PREFERENCE_MATERIAL_MELON, Id.minecraft("melon_slice"), 1),
        new PlayerPreference.Choice<>(Materials.PUMPKIN, HerbalismMessages.PREFERENCE_MATERIAL_PUMPKIN, Id.minecraft("pumpkin"), 1),
        new PlayerPreference.Choice<>(Materials.NETHER, HerbalismMessages.PREFERENCE_MATERIAL_NETHER, Id.minecraft("nether_wart"), 1),
        new PlayerPreference.Choice<>(Materials.FLOWERS, HerbalismMessages.PREFERENCE_MATERIAL_FLOWERS, Id.minecraft("dandelion"), 1),
        new PlayerPreference.Choice<>(Materials.FUNGI, HerbalismMessages.PREFERENCE_MATERIAL_FUNGI, Id.minecraft("red_mushroom"), 1),
        new PlayerPreference.Choice<>(Materials.LEAVES, HerbalismMessages.PREFERENCE_MATERIAL_LEAVES, Id.minecraft("oak_leaves"), 1),
        new PlayerPreference.Choice<>(Materials.BERRIES, HerbalismMessages.PREFERENCE_MATERIAL_BERRIES, Id.minecraft("sweet_berries"), 1),
        new PlayerPreference.Choice<>(Materials.KELP, HerbalismMessages.PREFERENCE_MATERIAL_KELP, Id.minecraft("kelp"), 1))));
  }

  static boolean isPlantingItem(Id material) {
    String name = material.legacyName();
    return name.endsWith("_SEEDS") || is(material, "carrot") || is(material, "potato")
        || is(material, "nether_wart") || name.endsWith("_SAPLING") || is(material, "pitcher_pod");
  }

  private static boolean is(Id material, String path) {
    return material.isMinecraft() && material.path().equals(path);
  }

  private static boolean tagged(Id tag, Id material) {
    return AdaptCore.platform().host().registries().isTagged(RegistryKind.BLOCK, tag, material);
  }

  enum Materials {
    ALL, WHEAT, CARROT, POTATO, BEETROOT, MELON, PUMPKIN, NETHER, FLOWERS, FUNGI, LEAVES, BERRIES, KELP;

    boolean allows(Id material) {
      String name = material.legacyName();
      return switch (this) {
        case ALL -> true;
        case NETHER -> is(material, "nether_wart") || is(material, "nether_wart_block");
        case FLOWERS -> name.contains("FLOWER") || name.contains("PITCHER") || tagged(FLOWERS_TAG, material);
        case FUNGI -> name.contains("MUSHROOM") || name.contains("FUNGUS");
        case LEAVES -> tagged(LEAVES_TAG, material);
        case BERRIES -> name.contains("BERR") || name.contains("CAVE_VINE");
        case KELP -> name.contains("KELP") || name.contains("SEAGRASS");
        default -> name.contains(name());
      };
    }
  }
}
