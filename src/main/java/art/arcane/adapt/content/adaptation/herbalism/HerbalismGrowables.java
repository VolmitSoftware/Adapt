package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class HerbalismGrowables {
  private static final Map<Material, Growable> BY_BLOCK = byBlock();

  private HerbalismGrowables() {
  }

  static PreferenceToggleGroup group(String id, TextKey label, Material icon) {
    List<PreferenceToggleGroup.Entry> entries = new ArrayList<>(Growable.values().length);
    for (Growable growable : Growable.values()) {
      entries.add(new PreferenceToggleGroup.Entry(growable.preference(), growable.icon()));
    }
    return new PreferenceToggleGroup(id, label, icon, entries);
  }

  static Growable of(Material block) {
    return BY_BLOCK.getOrDefault(block, Growable.OTHER);
  }

  static boolean blocked(PlayerPreference<?> preference, Predicate<Material> blacklisted) {
    for (Growable growable : Growable.values()) {
      if (growable.preference() == preference) {
        return blocked(growable, blacklisted);
      }
    }
    return false;
  }

  static boolean blocked(Growable growable, Predicate<Material> blacklisted) {
    if (growable.blocks().isEmpty()) {
      return false;
    }
    for (Material block : growable.blocks()) {
      if (!blacklisted.test(block)) {
        return false;
      }
    }
    return true;
  }

  private static Map<Material, Growable> byBlock() {
    Map<Material, Growable> blocks = new EnumMap<>(Material.class);
    for (Growable growable : Growable.values()) {
      for (Material block : growable.blocks()) {
        blocks.put(block, growable);
      }
    }
    return Collections.unmodifiableMap(blocks);
  }

  enum Growable {
    WHEAT("wheat", HerbalismMessages.PREFERENCE_GROWABLE_WHEAT, Material.WHEAT, Material.WHEAT),
    CARROTS("carrots", HerbalismMessages.PREFERENCE_GROWABLE_CARROTS, Material.CARROT, Material.CARROTS),
    POTATOES("potatoes", HerbalismMessages.PREFERENCE_GROWABLE_POTATOES, Material.POTATO, Material.POTATOES),
    BEETROOTS("beetroots", HerbalismMessages.PREFERENCE_GROWABLE_BEETROOTS, Material.BEETROOT, Material.BEETROOTS),
    MELONS("melons", HerbalismMessages.PREFERENCE_GROWABLE_MELONS, Material.MELON_SLICE, Material.MELON_STEM),
    PUMPKINS("pumpkins", HerbalismMessages.PREFERENCE_GROWABLE_PUMPKINS, Material.PUMPKIN, Material.PUMPKIN_STEM),
    NETHER_WART("nether-wart", HerbalismMessages.PREFERENCE_GROWABLE_NETHER_WART, Material.NETHER_WART, Material.NETHER_WART),
    SWEET_BERRIES("sweet-berries", HerbalismMessages.PREFERENCE_GROWABLE_SWEET_BERRIES, Material.SWEET_BERRIES, Material.SWEET_BERRY_BUSH),
    GLOW_BERRIES("glow-berries", HerbalismMessages.PREFERENCE_GROWABLE_GLOW_BERRIES, Material.GLOW_BERRIES, Material.CAVE_VINES),
    COCOA("cocoa", HerbalismMessages.PREFERENCE_GROWABLE_COCOA, Material.COCOA_BEANS, Material.COCOA),
    TORCHFLOWERS("torchflowers", HerbalismMessages.PREFERENCE_GROWABLE_TORCHFLOWERS, Material.TORCHFLOWER, Material.TORCHFLOWER_CROP),
    PITCHER_PLANTS("pitcher-plants", HerbalismMessages.PREFERENCE_GROWABLE_PITCHER_PLANTS, Material.PITCHER_PLANT, Material.PITCHER_CROP),
    SUGAR_CANE("sugar-cane", HerbalismMessages.PREFERENCE_GROWABLE_SUGAR_CANE, Material.SUGAR_CANE, Material.SUGAR_CANE),
    CACTUS("cactus", HerbalismMessages.PREFERENCE_GROWABLE_CACTUS, Material.CACTUS, Material.CACTUS),
    BAMBOO("bamboo", HerbalismMessages.PREFERENCE_GROWABLE_BAMBOO, Material.BAMBOO, Material.BAMBOO),
    KELP("kelp", HerbalismMessages.PREFERENCE_GROWABLE_KELP, Material.KELP, Material.KELP),
    NETHER_VINES("nether-vines", HerbalismMessages.PREFERENCE_GROWABLE_NETHER_VINES, Material.WEEPING_VINES, Material.WEEPING_VINES, Material.TWISTING_VINES),
    CHORUS("chorus", HerbalismMessages.PREFERENCE_GROWABLE_CHORUS, Material.CHORUS_FLOWER, Material.CHORUS_FLOWER),
    MANGROVE_PROPAGULES("mangrove-propagules", HerbalismMessages.PREFERENCE_GROWABLE_MANGROVE_PROPAGULES, Material.MANGROVE_PROPAGULE, Material.MANGROVE_PROPAGULE),
    OTHER("other", HerbalismMessages.PREFERENCE_GROWABLE_OTHER, Material.BONE_MEAL);

    private final PlayerPreference<CommonPreferences.Toggle> preference;
    private final Material icon;
    private final Set<Material> blocks;

    Growable(String id, TextKey label, Material icon, Material... blocks) {
      preference = CommonPreferences.toggle("growable-" + id, label);
      this.icon = icon;
      this.blocks = blocks.length == 0 ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(List.of(blocks)));
    }

    PlayerPreference<CommonPreferences.Toggle> preference() {
      return preference;
    }

    Material icon() {
      return icon;
    }

    Set<Material> blocks() {
      return blocks;
    }
  }
}
