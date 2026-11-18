package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.platform.value.Id;

/** Resolves the upper-case material name the hunter buff configs store ("ROTTEN_FLESH") to an item id. */
final class HunterConsumables {
  private HunterConsumables() {
  }

  static Id resolve(String materialName) {
    Id id = MaterialNames.exact(materialName);
    return MaterialNames.exists(id) ? id : null;
  }
}
