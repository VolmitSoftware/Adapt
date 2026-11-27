package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.item.MaterialNames;
import art.arcane.adapt.platform.value.Id;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HarvestCrops {
  static final List<String> DEFAULTS = List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "SWEET_BERRY_BUSH",
      "CAVE_VINES", "TORCHFLOWER_CROP", "PITCHER_CROP", "MELON", "PUMPKIN", "SUGAR_CANE", "BAMBOO", "CACTUS");

  static final Id TORCHFLOWER = Id.minecraft("torchflower");
  static final Id TORCHFLOWER_CROP = Id.minecraft("torchflower_crop");
  static final Id TORCHFLOWER_SEEDS = Id.minecraft("torchflower_seeds");
  static final Id CAVE_VINES = Id.minecraft("cave_vines");
  static final Id CAVE_VINES_PLANT = Id.minecraft("cave_vines_plant");
  static final Id SWEET_BERRY_BUSH = Id.minecraft("sweet_berry_bush");
  static final Id MELON = Id.minecraft("melon");
  static final Id PUMPKIN = Id.minecraft("pumpkin");
  static final Id NETHER_WART = Id.minecraft("nether_wart");

  private static final Map<Id, Id> SEEDS = Map.ofEntries(
      Map.entry(Id.minecraft("wheat"), Id.minecraft("wheat_seeds")),
      Map.entry(Id.minecraft("carrots"), Id.minecraft("carrot")),
      Map.entry(Id.minecraft("potatoes"), Id.minecraft("potato")),
      Map.entry(Id.minecraft("beetroots"), Id.minecraft("beetroot_seeds")),
      Map.entry(NETHER_WART, NETHER_WART),
      Map.entry(Id.minecraft("cocoa"), Id.minecraft("cocoa_beans")),
      Map.entry(Id.minecraft("pitcher_crop"), Id.minecraft("pitcher_pod")),
      Map.entry(TORCHFLOWER, TORCHFLOWER_SEEDS),
      Map.entry(TORCHFLOWER_CROP, TORCHFLOWER_SEEDS),
      Map.entry(SWEET_BERRY_BUSH, Id.minecraft("sweet_berries")),
      Map.entry(CAVE_VINES, Id.minecraft("glow_berries")),
      Map.entry(CAVE_VINES_PLANT, Id.minecraft("glow_berries")));
  private static final Map<Id, Id> FARMLAND_CROPS = Map.of(
      Id.minecraft("wheat_seeds"), Id.minecraft("wheat"),
      Id.minecraft("carrot"), Id.minecraft("carrots"),
      Id.minecraft("potato"), Id.minecraft("potatoes"),
      Id.minecraft("beetroot_seeds"), Id.minecraft("beetroots"),
      TORCHFLOWER_SEEDS, TORCHFLOWER_CROP,
      Id.minecraft("pitcher_pod"), Id.minecraft("pitcher_crop"));
  private static final Set<Id> UNTENDABLE = Set.of(NETHER_WART);
  private static final Set<Id> STEMS = Set.of(Id.minecraft("melon_stem"), Id.minecraft("pumpkin_stem"), Id.minecraft("attached_melon_stem"),
      Id.minecraft("attached_pumpkin_stem"));
  private static final Set<Id> COLUMNS = Set.of(Id.minecraft("sugar_cane"), Id.minecraft("bamboo"), Id.minecraft("cactus"));

  private final Map<Id, Kind> kinds;

  private HarvestCrops(Map<Id, Kind> kinds) {
    this.kinds = kinds;
  }

  static HarvestCrops parse(List<String> names) {
    Map<Id, Kind> kinds = new HashMap<>();
    if (names == null) {
      return new HarvestCrops(kinds);
    }
    for (String name : names) {
      Id type = name == null ? null : crop(name.trim());
      if (type != null && STEMS.contains(type)) {
        AdaptApi.log().warn("Harvester Core ignores " + type.legacyName() + " in its crops list because golems never harvest melon or pumpkin stems.");
        continue;
      }
      if (type != null) {
        add(kinds, type);
      }
    }
    return new HarvestCrops(kinds);
  }

  private static Id crop(String name) {
    Id vanilla = MaterialNames.exact(name.toUpperCase(Locale.ROOT));
    return vanilla != null ? vanilla : MaterialNames.modded(name);
  }

  static Id seed(Id crop) {
    return SEEDS.get(crop);
  }

  Kind kind(Id type) {
    return kinds.getOrDefault(type, Kind.NONE);
  }

  Id farmlandCrop(Id seed) {
    Id crop = FARMLAND_CROPS.get(seed);
    return crop != null && kinds.containsKey(crop) ? crop : null;
  }

  boolean tendable(Id type) {
    return switch (kind(type)) {
      case CROP, BERRY_BUSH -> !UNTENDABLE.contains(type);
      case TORCHFLOWER -> TORCHFLOWER_CROP.equals(type);
      case NONE, CAVE_VINES, GOURD, COLUMN -> false;
    };
  }

  private static void add(Map<Id, Kind> kinds, Id type) {
    if (TORCHFLOWER_CROP.equals(type) || TORCHFLOWER.equals(type)) {
      kinds.put(TORCHFLOWER_CROP, Kind.TORCHFLOWER);
      kinds.put(TORCHFLOWER, Kind.TORCHFLOWER);
    } else if (CAVE_VINES.equals(type) || CAVE_VINES_PLANT.equals(type)) {
      kinds.put(CAVE_VINES, Kind.CAVE_VINES);
      kinds.put(CAVE_VINES_PLANT, Kind.CAVE_VINES);
    } else if (SWEET_BERRY_BUSH.equals(type)) {
      kinds.put(type, Kind.BERRY_BUSH);
    } else if (MELON.equals(type) || PUMPKIN.equals(type)) {
      kinds.put(type, Kind.GOURD);
    } else if (COLUMNS.contains(type)) {
      kinds.put(type, Kind.COLUMN);
    } else {
      kinds.put(type, Kind.CROP);
    }
  }

  enum Kind {
    NONE,
    CROP,
    TORCHFLOWER,
    BERRY_BUSH,
    CAVE_VINES,
    GOURD,
    COLUMN
  }
}
