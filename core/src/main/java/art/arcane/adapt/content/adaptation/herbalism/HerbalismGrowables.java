package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class HerbalismGrowables {
  private static final Map<Id, Growable> BY_BLOCK = byBlock();

  private HerbalismGrowables() {
  }

  static PreferenceToggleGroup group(String id, TextKey label, Id icon) {
    List<PreferenceToggleGroup.Entry> entries = new ArrayList<>(Growable.values().length);
    for (Growable growable : Growable.values()) {
      entries.add(new PreferenceToggleGroup.Entry(growable.preference(), growable.icon()));
    }
    return new PreferenceToggleGroup(id, label, icon, entries);
  }

  static Growable of(Id block) {
    return BY_BLOCK.getOrDefault(block, Growable.OTHER);
  }

  static boolean blocked(PlayerPreference<?> preference, Predicate<Id> blacklisted) {
    for (Growable growable : Growable.values()) {
      if (growable.preference() == preference) {
        return blocked(growable, blacklisted);
      }
    }
    return false;
  }

  static boolean blocked(Growable growable, Predicate<Id> blacklisted) {
    if (growable.blocks().isEmpty()) {
      return false;
    }
    for (Id block : growable.blocks()) {
      if (!blacklisted.test(block)) {
        return false;
      }
    }
    return true;
  }

  private static Map<Id, Growable> byBlock() {
    Map<Id, Growable> blocks = new HashMap<>();
    for (Growable growable : Growable.values()) {
      for (Id block : growable.blocks()) {
        blocks.put(block, growable);
      }
    }
    return Collections.unmodifiableMap(blocks);
  }

  enum Growable {
    WHEAT("wheat", HerbalismMessages.PREFERENCE_GROWABLE_WHEAT, Id.minecraft("wheat"), Id.minecraft("wheat")),
    CARROTS("carrots", HerbalismMessages.PREFERENCE_GROWABLE_CARROTS, Id.minecraft("carrot"), Id.minecraft("carrots")),
    POTATOES("potatoes", HerbalismMessages.PREFERENCE_GROWABLE_POTATOES, Id.minecraft("potato"), Id.minecraft("potatoes")),
    BEETROOTS("beetroots", HerbalismMessages.PREFERENCE_GROWABLE_BEETROOTS, Id.minecraft("beetroot"), Id.minecraft("beetroots")),
    MELONS("melons", HerbalismMessages.PREFERENCE_GROWABLE_MELONS, Id.minecraft("melon_slice"), Id.minecraft("melon_stem")),
    PUMPKINS("pumpkins", HerbalismMessages.PREFERENCE_GROWABLE_PUMPKINS, Id.minecraft("pumpkin"), Id.minecraft("pumpkin_stem")),
    NETHER_WART("nether-wart", HerbalismMessages.PREFERENCE_GROWABLE_NETHER_WART, Id.minecraft("nether_wart"), Id.minecraft("nether_wart")),
    SWEET_BERRIES("sweet-berries", HerbalismMessages.PREFERENCE_GROWABLE_SWEET_BERRIES, Id.minecraft("sweet_berries"), Id.minecraft("sweet_berry_bush")),
    GLOW_BERRIES("glow-berries", HerbalismMessages.PREFERENCE_GROWABLE_GLOW_BERRIES, Id.minecraft("glow_berries"), Id.minecraft("cave_vines")),
    COCOA("cocoa", HerbalismMessages.PREFERENCE_GROWABLE_COCOA, Id.minecraft("cocoa_beans"), Id.minecraft("cocoa")),
    TORCHFLOWERS("torchflowers", HerbalismMessages.PREFERENCE_GROWABLE_TORCHFLOWERS, Id.minecraft("torchflower"), Id.minecraft("torchflower_crop")),
    PITCHER_PLANTS("pitcher-plants", HerbalismMessages.PREFERENCE_GROWABLE_PITCHER_PLANTS, Id.minecraft("pitcher_plant"), Id.minecraft("pitcher_crop")),
    SUGAR_CANE("sugar-cane", HerbalismMessages.PREFERENCE_GROWABLE_SUGAR_CANE, Id.minecraft("sugar_cane"), Id.minecraft("sugar_cane")),
    CACTUS("cactus", HerbalismMessages.PREFERENCE_GROWABLE_CACTUS, Id.minecraft("cactus"), Id.minecraft("cactus")),
    BAMBOO("bamboo", HerbalismMessages.PREFERENCE_GROWABLE_BAMBOO, Id.minecraft("bamboo"), Id.minecraft("bamboo")),
    KELP("kelp", HerbalismMessages.PREFERENCE_GROWABLE_KELP, Id.minecraft("kelp"), Id.minecraft("kelp")),
    NETHER_VINES("nether-vines", HerbalismMessages.PREFERENCE_GROWABLE_NETHER_VINES, Id.minecraft("weeping_vines"), Id.minecraft("weeping_vines"), Id.minecraft("twisting_vines")),
    CHORUS("chorus", HerbalismMessages.PREFERENCE_GROWABLE_CHORUS, Id.minecraft("chorus_flower"), Id.minecraft("chorus_flower")),
    MANGROVE_PROPAGULES("mangrove-propagules", HerbalismMessages.PREFERENCE_GROWABLE_MANGROVE_PROPAGULES, Id.minecraft("mangrove_propagule"), Id.minecraft("mangrove_propagule")),
    OTHER("other", HerbalismMessages.PREFERENCE_GROWABLE_OTHER, Id.minecraft("bone_meal"));

    private final PlayerPreference<CommonPreferences.Toggle> preference;
    private final Id icon;
    private final Set<Id> blocks;

    Growable(String id, TextKey label, Id icon, Id... blocks) {
      preference = CommonPreferences.toggle("growable-" + id, label);
      this.icon = icon;
      this.blocks = blocks.length == 0 ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(List.of(blocks)));
    }

    PlayerPreference<CommonPreferences.Toggle> preference() {
      return preference;
    }

    Id icon() {
      return icon;
    }

    Set<Id> blocks() {
      return blocks;
    }
  }
}
