package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TreeBlocks {
  private static final String LEGACY = "LEGACY_";
  private static final String STRIPPED = "STRIPPED_";
  private static final List<String> WOOD_SUFFIXES = List.of("_LOG", "_WOOD");
  private static final List<String> SAPLING_SUFFIXES = List.of("_SAPLING", "_PROPAGULE", "_FUNGUS");
  private static final List<String> BUILDING_PREFIXES = List.of("POTTED_", "POLISHED_", "SMOOTH_", "CUT_", "CHISELED_");
  private static final List<String> BUILDING_SUFFIXES = List.of("_PLANKS", "_STAIRS", "_SLAB", "_FENCE", "_FENCE_GATE", "_WALL", "_DOOR",
      "_TRAPDOOR", "GLASS", "_PANE", "_WOOL", "_CARPET", "_CONCRETE", "_GLAZED_TERRACOTTA", "_BED", "_BANNER", "_SIGN", "_BUTTON",
      "_PRESSURE_PLATE", "BRICKS", "_TILES", "CANDLE", "_BARS", "SHULKER_BOX", "LANTERN", "TORCH", "RAIL", "CHEST", "_SHELF", "CHAIN",
      "_COPPER", "_GRATE", "_BULB");
  private static final Set<String> BUILDING_NAMES = Set.of("COBBLESTONE", "BOOKSHELF", "LADDER", "CRAFTING_TABLE", "FURNACE",
      "BLAST_FURNACE", "SMOKER", "BARREL", "LECTERN", "LEVER", "QUARTZ_BLOCK", "QUARTZ_PILLAR", "SCAFFOLDING", "HAY_BLOCK", "FLOWER_POT",
      "TARGET", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "CAULDRON", "COMPOSTER", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE",
      "SMITHING_TABLE", "LOOM", "STONECUTTER", "GRINDSTONE", "BELL", "BREWING_STAND", "ENCHANTING_TABLE", "JUKEBOX", "NOTE_BLOCK",
      "BEACON", "CAMPFIRE", "SOUL_CAMPFIRE", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK", "LAPIS_BLOCK",
      "REDSTONE_BLOCK", "NETHERITE_BLOCK", "COAL_BLOCK", "COPPER_BLOCK", "TNT", "PACKED_MUD", "DECORATED_POT", "BEEHIVE", "HOPPER",
      "DISPENSER", "DROPPER", "OBSERVER", "PISTON", "STICKY_PISTON", "REPEATER", "COMPARATOR", "REDSTONE_WIRE", "DAYLIGHT_DETECTOR",
      "TRIPWIRE_HOOK", "END_ROD", "LIGHTNING_ROD", "CRAFTER", "SEA_LANTERN", "GLOWSTONE", "REDSTONE_LAMP", "SPONGE", "WET_SPONGE");
  private static final Set<String> NOT_BUILDING = Set.of("MOSS_CARPET", "PALE_MOSS_CARPET", "SMOOTH_BASALT");
  private static final Set<Material> STEMS = EnumSet.of(Material.CRIMSON_STEM, Material.WARPED_STEM);
  private static final Set<Material> PAIRED = EnumSet.of(Material.DARK_OAK_SAPLING, Material.PALE_OAK_SAPLING);
  private static final Set<Material> CAPS = EnumSet.of(Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK, Material.SHROOMLIGHT);
  private static final Set<Material> SOIL = EnumSet.of(Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT,
      Material.ROOTED_DIRT, Material.MYCELIUM, Material.MOSS_BLOCK, Material.PALE_MOSS_BLOCK, Material.MUD, Material.MUDDY_MANGROVE_ROOTS,
      Material.FARMLAND);
  private static final Set<Material> GROUND = EnumSet.of(Material.STONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE,
      Material.TUFF, Material.CALCITE, Material.DEEPSLATE, Material.GRAVEL, Material.CLAY, Material.SAND, Material.RED_SAND,
      Material.SNOW_BLOCK, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);
  private static final Set<Material> NYLIUM = EnumSet.of(Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM);
  private static final Set<Material> WOOD = EnumSet.noneOf(Material.class);
  private static final Set<Material> LEAVES = EnumSet.noneOf(Material.class);
  private static final Set<Material> BUILDING = EnumSet.noneOf(Material.class);
  private static final Map<Material, Material> SAPLINGS = new EnumMap<>(Material.class);

  static {
    GROUND.addAll(SOIL);
    for (Material type : Material.values()) {
      String name = type.name();
      if (name.startsWith(LEGACY)) {
        continue;
      }
      if (wood(name)) {
        WOOD.add(type);
      } else if (name.endsWith("_LEAVES")) {
        LEAVES.add(type);
      } else if (name.endsWith("TERRACOTTA") && !name.endsWith("_GLAZED_TERRACOTTA")) {
        GROUND.add(type);
      } else if (building(name)) {
        BUILDING.add(type);
      }
    }
    for (Material type : WOOD) {
      sapling(type, family(type.name()));
    }
    for (Material type : STEMS) {
      sapling(type, family(type.name()));
    }
  }

  private TreeBlocks() {
  }

  static boolean trunk(Material type, boolean nether) {
    return WOOD.contains(type) || (nether && STEMS.contains(type));
  }

  static boolean stem(Material type) {
    return STEMS.contains(type);
  }

  static boolean leaves(Material type) {
    return LEAVES.contains(type);
  }

  static boolean cap(Material type) {
    return CAPS.contains(type);
  }

  static boolean soil(Material type) {
    return SOIL.contains(type);
  }

  static boolean ground(Material type) {
    return GROUND.contains(type);
  }

  static boolean nylium(Material type) {
    return NYLIUM.contains(type);
  }

  static boolean building(Material type) {
    return BUILDING.contains(type);
  }

  static boolean air(Material type) {
    return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
  }

  static Material sapling(Material species) {
    return SAPLINGS.get(species);
  }

  static boolean growsAlone(Material sapling) {
    return !PAIRED.contains(sapling);
  }

  private static boolean wood(String name) {
    for (String suffix : WOOD_SUFFIXES) {
      if (name.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean building(String name) {
    if (NOT_BUILDING.contains(name)) {
      return false;
    }
    if (BUILDING_NAMES.contains(name)) {
      return true;
    }
    for (String prefix : BUILDING_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    for (String suffix : BUILDING_SUFFIXES) {
      if (name.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static String family(String name) {
    String bare = name.startsWith(STRIPPED) ? name.substring(STRIPPED.length()) : name;
    return bare.substring(0, bare.lastIndexOf('_'));
  }

  private static void sapling(Material species, String family) {
    for (String suffix : SAPLING_SUFFIXES) {
      Material sapling = Material.getMaterial(family + suffix);
      if (sapling != null) {
        SAPLINGS.put(species, sapling);
        return;
      }
    }
  }
}
