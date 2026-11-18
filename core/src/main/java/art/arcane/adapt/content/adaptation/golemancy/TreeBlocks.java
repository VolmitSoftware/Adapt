package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class TreeBlocks {
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
  private static final Set<Id> STEMS = Set.of(Id.minecraft("crimson_stem"), Id.minecraft("warped_stem"));
  private static final Set<Id> PAIRED = Set.of(Id.minecraft("dark_oak_sapling"), Id.minecraft("pale_oak_sapling"));
  private static final Set<Id> CAPS = Set.of(Id.minecraft("nether_wart_block"), Id.minecraft("warped_wart_block"),
      Id.minecraft("shroomlight"));
  private static final Set<Id> SOIL = Set.of(Id.minecraft("dirt"), Id.minecraft("grass_block"), Id.minecraft("podzol"),
      Id.minecraft("coarse_dirt"), Id.minecraft("rooted_dirt"), Id.minecraft("mycelium"), Id.minecraft("moss_block"),
      Id.minecraft("pale_moss_block"), Id.minecraft("mud"), Id.minecraft("muddy_mangrove_roots"), Id.minecraft("farmland"));
  private static final Set<Id> STONES = Set.of(Id.minecraft("stone"), Id.minecraft("andesite"), Id.minecraft("diorite"),
      Id.minecraft("granite"), Id.minecraft("tuff"), Id.minecraft("calcite"), Id.minecraft("deepslate"), Id.minecraft("gravel"),
      Id.minecraft("clay"), Id.minecraft("sand"), Id.minecraft("red_sand"), Id.minecraft("snow_block"), Id.minecraft("ice"),
      Id.minecraft("packed_ice"), Id.minecraft("blue_ice"));
  private static final Set<Id> NYLIUM = Set.of(Id.minecraft("crimson_nylium"), Id.minecraft("warped_nylium"));
  private static final Map<String, String> VANILLA_SAPLINGS = Map.ofEntries(Map.entry("OAK", "oak_sapling"),
      Map.entry("SPRUCE", "spruce_sapling"), Map.entry("BIRCH", "birch_sapling"), Map.entry("JUNGLE", "jungle_sapling"),
      Map.entry("ACACIA", "acacia_sapling"), Map.entry("DARK_OAK", "dark_oak_sapling"), Map.entry("PALE_OAK", "pale_oak_sapling"),
      Map.entry("CHERRY", "cherry_sapling"), Map.entry("MANGROVE", "mangrove_propagule"), Map.entry("CRIMSON", "crimson_fungus"),
      Map.entry("WARPED", "warped_fungus"));
  private static final Map<Id, Kind> KINDS = new ConcurrentHashMap<>();
  private static final Map<Id, Optional<Id>> SAPLINGS = new ConcurrentHashMap<>();

  private TreeBlocks() {
  }

  static boolean trunk(Id type, boolean nether) {
    return kind(type) == Kind.WOOD || (nether && STEMS.contains(type));
  }

  static boolean stem(Id type) {
    return STEMS.contains(type);
  }

  static boolean leaves(Id type) {
    return kind(type) == Kind.LEAVES;
  }

  static boolean cap(Id type) {
    return CAPS.contains(type);
  }

  static boolean soil(Id type) {
    return SOIL.contains(type);
  }

  static boolean ground(Id type) {
    return SOIL.contains(type) || STONES.contains(type) || kind(type) == Kind.TERRACOTTA;
  }

  static boolean nylium(Id type) {
    return NYLIUM.contains(type);
  }

  static boolean building(Id type) {
    return kind(type) == Kind.BUILDING;
  }

  static boolean air(Id type) {
    return type != null && type.isAir();
  }

  static Id sapling(Id species) {
    if (species == null || (kind(species) != Kind.WOOD && !STEMS.contains(species))) {
      return null;
    }
    return SAPLINGS.computeIfAbsent(species, TreeBlocks::findSapling).orElse(null);
  }

  static boolean growsAlone(Id sapling) {
    return !PAIRED.contains(sapling);
  }

  private static Kind kind(Id type) {
    if (type == null) {
      return Kind.NONE;
    }
    return KINDS.computeIfAbsent(type, TreeBlocks::classify);
  }

  private static Kind classify(Id type) {
    String name = type.legacyName();
    if (wood(name)) {
      return Kind.WOOD;
    }
    if (name.endsWith("_LEAVES")) {
      return Kind.LEAVES;
    }
    if (name.endsWith("TERRACOTTA") && !name.endsWith("_GLAZED_TERRACOTTA")) {
      return Kind.TERRACOTTA;
    }
    return building(name) ? Kind.BUILDING : Kind.NONE;
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

  private static Optional<Id> findSapling(Id species) {
    String name = species.legacyName();
    String bare = name.startsWith(STRIPPED) ? name.substring(STRIPPED.length()) : name;
    String family = bare.substring(0, bare.lastIndexOf('_'));
    String vanilla = species.isMinecraft() ? VANILLA_SAPLINGS.get(family) : null;
    if (vanilla != null) {
      return Optional.of(Id.minecraft(vanilla));
    }
    AdaptCore core = AdaptCore.current();
    if (core == null) {
      return Optional.empty();
    }
    for (String suffix : SAPLING_SUFFIXES) {
      Id sapling = species.withPath((family + suffix).toLowerCase(Locale.ROOT));
      if (core.getPlatform().host().registries().exists(RegistryKind.BLOCK, sapling)) {
        return Optional.of(sapling);
      }
    }
    return Optional.empty();
  }

  private enum Kind {
    WOOD,
    LEAVES,
    TERRACOTTA,
    BUILDING,
    NONE
  }
}
