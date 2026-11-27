package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

final class HerbalismPreferences {
  private static final Id FLOWERS_TAG = Id.minecraft("flowers");
  private static final Id LEAVES_TAG = Id.minecraft("leaves");

  private HerbalismPreferences() {
  }

  static PlayerPreference<Materials> materials(String id, Text label) {
    return PlayerPreference.of(id, Materials.class, Materials.ALL, List.of(
        new PlayerPreference.Choice<>(Materials.ALL, Text.key("adapt.herbalism.preferences.material.all"), Id.minecraft("chest"), 1),
        new PlayerPreference.Choice<>(Materials.WHEAT, Text.key("adapt.herbalism.preferences.material.wheat"), Id.minecraft("wheat"), 1),
        new PlayerPreference.Choice<>(Materials.CARROT, Text.key("adapt.herbalism.preferences.material.carrot"), Id.minecraft("carrot"), 1),
        new PlayerPreference.Choice<>(Materials.POTATO, Text.key("adapt.herbalism.preferences.material.potato"), Id.minecraft("potato"), 1),
        new PlayerPreference.Choice<>(Materials.BEETROOT, Text.key("adapt.herbalism.preferences.material.beetroot"), Id.minecraft("beetroot"), 1),
        new PlayerPreference.Choice<>(Materials.MELON, Text.key("adapt.herbalism.preferences.material.melon"), Id.minecraft("melon_slice"), 1),
        new PlayerPreference.Choice<>(Materials.PUMPKIN, Text.key("adapt.herbalism.preferences.material.pumpkin"), Id.minecraft("pumpkin"), 1),
        new PlayerPreference.Choice<>(Materials.NETHER, Text.key("adapt.herbalism.preferences.material.nether"), Id.minecraft("nether_wart"), 1),
        new PlayerPreference.Choice<>(Materials.FLOWERS, Text.key("adapt.herbalism.preferences.material.flowers"), Id.minecraft("dandelion"), 1),
        new PlayerPreference.Choice<>(Materials.FUNGI, Text.key("adapt.herbalism.preferences.material.fungi"), Id.minecraft("red_mushroom"), 1),
        new PlayerPreference.Choice<>(Materials.LEAVES, Text.key("adapt.herbalism.preferences.material.leaves"), Id.minecraft("oak_leaves"), 1),
        new PlayerPreference.Choice<>(Materials.BERRIES, Text.key("adapt.herbalism.preferences.material.berries"), Id.minecraft("sweet_berries"), 1),
        new PlayerPreference.Choice<>(Materials.KELP, Text.key("adapt.herbalism.preferences.material.kelp"), Id.minecraft("kelp"), 1))).labelled(label);
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
    return AdaptApi.platform().host().registries().isTagged(RegistryKind.BLOCK, tag, material);
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
