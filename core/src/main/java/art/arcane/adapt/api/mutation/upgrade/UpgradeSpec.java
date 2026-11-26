package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.mutation.blocks.UpgradedAdaptation;

/**
 * Where one of the 72 upgraded adaptations lives: its adaptation id, its localization prefix and the class that must
 * implement it, all derived from {@link UpgradedAdaptation#name()}.
 */
public record UpgradeSpec(String skillId, UpgradedAdaptation upgrade, String id, String localizationKey, String className) {
  public String nameKey() {
    return localizationKey + ".name";
  }

  public String descriptionKey() {
    return localizationKey + ".description";
  }
}
