package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HarvestCrops {
  static final List<String> DEFAULTS = List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "SWEET_BERRY_BUSH",
      "CAVE_VINES", "TORCHFLOWER_CROP", "PITCHER_CROP", "MELON", "PUMPKIN", "SUGAR_CANE", "BAMBOO", "CACTUS");

  private static final Map<Material, Material> SEEDS = Map.ofEntries(
      Map.entry(Material.WHEAT, Material.WHEAT_SEEDS),
      Map.entry(Material.CARROTS, Material.CARROT),
      Map.entry(Material.POTATOES, Material.POTATO),
      Map.entry(Material.BEETROOTS, Material.BEETROOT_SEEDS),
      Map.entry(Material.NETHER_WART, Material.NETHER_WART),
      Map.entry(Material.COCOA, Material.COCOA_BEANS),
      Map.entry(Material.PITCHER_CROP, Material.PITCHER_POD),
      Map.entry(Material.TORCHFLOWER, Material.TORCHFLOWER_SEEDS),
      Map.entry(Material.TORCHFLOWER_CROP, Material.TORCHFLOWER_SEEDS),
      Map.entry(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES),
      Map.entry(Material.CAVE_VINES, Material.GLOW_BERRIES),
      Map.entry(Material.CAVE_VINES_PLANT, Material.GLOW_BERRIES));
  private static final Map<Material, Material> FARMLAND_CROPS = Map.of(
      Material.WHEAT_SEEDS, Material.WHEAT,
      Material.CARROT, Material.CARROTS,
      Material.POTATO, Material.POTATOES,
      Material.BEETROOT_SEEDS, Material.BEETROOTS,
      Material.TORCHFLOWER_SEEDS, Material.TORCHFLOWER_CROP,
      Material.PITCHER_POD, Material.PITCHER_CROP);
  private static final Set<Material> UNTENDABLE = Set.of(Material.NETHER_WART);

  private final Map<Material, Kind> kinds;

  private HarvestCrops(Map<Material, Kind> kinds) {
    this.kinds = kinds;
  }

  static HarvestCrops parse(List<String> names) {
    Map<Material, Kind> kinds = new EnumMap<>(Material.class);
    if (names == null) {
      return new HarvestCrops(kinds);
    }
    for (String name : names) {
      Material type = name == null ? null : Material.getMaterial(name.trim().toUpperCase(Locale.ROOT));
      if (type != null) {
        add(kinds, type);
      }
    }
    return new HarvestCrops(kinds);
  }

  static Material seed(Material crop) {
    return SEEDS.get(crop);
  }

  Kind kind(Material type) {
    return kinds.getOrDefault(type, Kind.NONE);
  }

  Material farmlandCrop(Material seed) {
    Material crop = FARMLAND_CROPS.get(seed);
    return crop != null && kinds.containsKey(crop) ? crop : null;
  }

  boolean tendable(Material type) {
    return switch (kind(type)) {
      case CROP, BERRY_BUSH -> !UNTENDABLE.contains(type);
      case TORCHFLOWER -> type == Material.TORCHFLOWER_CROP;
      case NONE, CAVE_VINES, GOURD, COLUMN -> false;
    };
  }

  private static void add(Map<Material, Kind> kinds, Material type) {
    switch (type) {
      case TORCHFLOWER_CROP, TORCHFLOWER -> {
        kinds.put(Material.TORCHFLOWER_CROP, Kind.TORCHFLOWER);
        kinds.put(Material.TORCHFLOWER, Kind.TORCHFLOWER);
      }
      case CAVE_VINES, CAVE_VINES_PLANT -> {
        kinds.put(Material.CAVE_VINES, Kind.CAVE_VINES);
        kinds.put(Material.CAVE_VINES_PLANT, Kind.CAVE_VINES);
      }
      case SWEET_BERRY_BUSH -> kinds.put(type, Kind.BERRY_BUSH);
      case MELON, PUMPKIN -> kinds.put(type, Kind.GOURD);
      case SUGAR_CANE, BAMBOO, CACTUS -> kinds.put(type, Kind.COLUMN);
      default -> kinds.put(type, Kind.CROP);
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
