package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class HerbalismGrowables {
  private static final Map<Id, Growable> BY_BLOCK = byBlock();

  private HerbalismGrowables() {
  }

  static PreferenceToggleGroup group(String id, Text label, Id icon) {
    List<PreferenceToggleGroup.Entry> entries = new ArrayList<>(Growable.values().length);
    for (Growable growable : Growable.values()) {
      entries.add(new PreferenceToggleGroup.Entry(growable.preference(), growable.icon()));
    }
    return new PreferenceToggleGroup(id, label, icon, entries);
  }

  static Growable of(Id block) {
    return BY_BLOCK.getOrDefault(block, Growable.OTHER);
  }

  /** Whether the block is a crop or plant the growth adaptations know. */
  public static boolean isCrop(Id block) {
    return BY_BLOCK.containsKey(block);
  }

  /** Whether the item is the produce of a known crop, such as wheat, a carrot or a melon slice. */
  public static boolean isProduce(Id item) {
    for (Growable growable : Growable.values()) {
      if (growable != Growable.OTHER && growable.icon().equals(item)) {
        return true;
      }
    }
    return false;
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
    WHEAT("wheat", Text.key("adapt.herbalism.preferences.growable.wheat"), Id.minecraft("wheat"), Id.minecraft("wheat")),
    CARROTS("carrots", Text.key("adapt.herbalism.preferences.growable.carrots"), Id.minecraft("carrot"), Id.minecraft("carrots")),
    POTATOES("potatoes", Text.key("adapt.herbalism.preferences.growable.potatoes"), Id.minecraft("potato"), Id.minecraft("potatoes")),
    BEETROOTS("beetroots", Text.key("adapt.herbalism.preferences.growable.beetroots"), Id.minecraft("beetroot"), Id.minecraft("beetroots")),
    MELONS("melons", Text.key("adapt.herbalism.preferences.growable.melons"), Id.minecraft("melon_slice"), Id.minecraft("melon_stem")),
    PUMPKINS("pumpkins", Text.key("adapt.herbalism.preferences.growable.pumpkins"), Id.minecraft("pumpkin"), Id.minecraft("pumpkin_stem")),
    NETHER_WART("nether-wart", Text.key("adapt.herbalism.preferences.growable.nether-wart"), Id.minecraft("nether_wart"), Id.minecraft("nether_wart")),
    SWEET_BERRIES("sweet-berries", Text.key("adapt.herbalism.preferences.growable.sweet-berries"), Id.minecraft("sweet_berries"), Id.minecraft("sweet_berry_bush")),
    GLOW_BERRIES("glow-berries", Text.key("adapt.herbalism.preferences.growable.glow-berries"), Id.minecraft("glow_berries"), Id.minecraft("cave_vines")),
    COCOA("cocoa", Text.key("adapt.herbalism.preferences.growable.cocoa"), Id.minecraft("cocoa_beans"), Id.minecraft("cocoa")),
    TORCHFLOWERS("torchflowers", Text.key("adapt.herbalism.preferences.growable.torchflowers"), Id.minecraft("torchflower"), Id.minecraft("torchflower_crop")),
    PITCHER_PLANTS("pitcher-plants", Text.key("adapt.herbalism.preferences.growable.pitcher-plants"), Id.minecraft("pitcher_plant"), Id.minecraft("pitcher_crop")),
    SUGAR_CANE("sugar-cane", Text.key("adapt.herbalism.preferences.growable.sugar-cane"), Id.minecraft("sugar_cane"), Id.minecraft("sugar_cane")),
    CACTUS("cactus", Text.key("adapt.herbalism.preferences.growable.cactus"), Id.minecraft("cactus"), Id.minecraft("cactus")),
    BAMBOO("bamboo", Text.key("adapt.herbalism.preferences.growable.bamboo"), Id.minecraft("bamboo"), Id.minecraft("bamboo")),
    KELP("kelp", Text.key("adapt.herbalism.preferences.growable.kelp"), Id.minecraft("kelp"), Id.minecraft("kelp")),
    NETHER_VINES("nether-vines", Text.key("adapt.herbalism.preferences.growable.nether-vines"), Id.minecraft("weeping_vines"), Id.minecraft("weeping_vines"), Id.minecraft("twisting_vines")),
    CHORUS("chorus", Text.key("adapt.herbalism.preferences.growable.chorus"), Id.minecraft("chorus_flower"), Id.minecraft("chorus_flower")),
    MANGROVE_PROPAGULES("mangrove-propagules", Text.key("adapt.herbalism.preferences.growable.mangrove-propagules"), Id.minecraft("mangrove_propagule"), Id.minecraft("mangrove_propagule")),
    OTHER("other", Text.key("adapt.herbalism.preferences.growable.other"), Id.minecraft("bone_meal"));

    private final PlayerPreference<CommonPreferences.Toggle> preference;
    private final Id icon;
    private final Set<Id> blocks;

    Growable(String id, Text label, Id icon, Id... blocks) {
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
