package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class TreeBlocks {
  private static final Map<Material, Material> SAPLINGS = Map.ofEntries(
      Map.entry(Material.OAK_LOG, Material.OAK_SAPLING),
      Map.entry(Material.SPRUCE_LOG, Material.SPRUCE_SAPLING),
      Map.entry(Material.BIRCH_LOG, Material.BIRCH_SAPLING),
      Map.entry(Material.JUNGLE_LOG, Material.JUNGLE_SAPLING),
      Map.entry(Material.ACACIA_LOG, Material.ACACIA_SAPLING),
      Map.entry(Material.DARK_OAK_LOG, Material.DARK_OAK_SAPLING),
      Map.entry(Material.MANGROVE_LOG, Material.MANGROVE_PROPAGULE),
      Map.entry(Material.CHERRY_LOG, Material.CHERRY_SAPLING),
      Map.entry(Material.PALE_OAK_LOG, Material.PALE_OAK_SAPLING),
      Map.entry(Material.CRIMSON_STEM, Material.CRIMSON_FUNGUS),
      Map.entry(Material.WARPED_STEM, Material.WARPED_FUNGUS));
  private static final Set<Material> STEMS = EnumSet.of(Material.CRIMSON_STEM, Material.WARPED_STEM);
  private static final Set<Material> PAIRED = EnumSet.of(Material.DARK_OAK_LOG, Material.PALE_OAK_LOG);
  private static final Set<Material> MEGA = EnumSet.of(Material.SPRUCE_LOG, Material.JUNGLE_LOG, Material.DARK_OAK_LOG, Material.PALE_OAK_LOG);
  private static final Set<Material> LEAVES = EnumSet.of(Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
      Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
      Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.PALE_OAK_LEAVES);
  private static final Set<Material> CAPS = EnumSet.of(Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK, Material.SHROOMLIGHT);
  private static final Set<Material> SOIL = EnumSet.of(Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT,
      Material.ROOTED_DIRT, Material.MYCELIUM, Material.MOSS_BLOCK, Material.PALE_MOSS_BLOCK, Material.MUD, Material.MUDDY_MANGROVE_ROOTS,
      Material.FARMLAND);
  private static final Set<Material> NYLIUM = EnumSet.of(Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM);
  private static final Set<Material> NATURAL = natural();

  private TreeBlocks() {
  }

  static boolean trunk(Material type, boolean nether) {
    return SAPLINGS.containsKey(type) && (nether || !STEMS.contains(type));
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

  static boolean nylium(Material type) {
    return NYLIUM.contains(type);
  }

  static boolean natural(Material type) {
    return NATURAL.contains(type);
  }

  static boolean air(Material type) {
    return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
  }

  static Material sapling(Material species) {
    return SAPLINGS.get(species);
  }

  static boolean growsAlone(Material species) {
    return !PAIRED.contains(species);
  }

  static boolean mega(Material species) {
    return MEGA.contains(species);
  }

  private static Set<Material> natural() {
    Set<Material> natural = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.WATER, Material.SNOW, Material.SNOW_BLOCK,
        Material.POWDER_SNOW, Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.DEEPSLATE, Material.TUFF,
        Material.CALCITE, Material.GRAVEL, Material.SAND, Material.RED_SAND, Material.CLAY, Material.MANGROVE_ROOTS, Material.NETHERRACK,
        Material.SOUL_SOIL, Material.SOUL_SAND, Material.BASALT, Material.BLACKSTONE, Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN,
        Material.LARGE_FERN, Material.SHORT_DRY_GRASS, Material.TALL_DRY_GRASS, Material.BUSH, Material.FIREFLY_BUSH, Material.DEAD_BUSH,
        Material.LEAF_LITTER, Material.MOSS_CARPET, Material.PALE_MOSS_CARPET, Material.PALE_HANGING_MOSS, Material.GLOW_LICHEN,
        Material.HANGING_ROOTS, Material.VINE, Material.COCOA, Material.BEE_NEST, Material.RESIN_CLUMP, Material.SWEET_BERRY_BUSH,
        Material.SUGAR_CANE, Material.SEAGRASS, Material.TALL_SEAGRASS, Material.LILY_PAD, Material.AZALEA, Material.FLOWERING_AZALEA,
        Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK, Material.MUSHROOM_STEM,
        Material.BAMBOO, Material.BAMBOO_SAPLING, Material.MELON, Material.PUMPKIN, Material.MOSSY_COBBLESTONE,
        Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP,
        Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.CORNFLOWER,
        Material.LILY_OF_THE_VALLEY, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.PINK_PETALS,
        Material.WILDFLOWERS, Material.OPEN_EYEBLOSSOM, Material.CLOSED_EYEBLOSSOM, Material.CRIMSON_ROOTS, Material.WARPED_ROOTS,
        Material.NETHER_SPROUTS, Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT, Material.TWISTING_VINES,
        Material.TWISTING_VINES_PLANT, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS, Material.OAK_SAPLING, Material.SPRUCE_SAPLING,
        Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING, Material.CHERRY_SAPLING,
        Material.PALE_OAK_SAPLING, Material.MANGROVE_PROPAGULE);
    natural.addAll(SOIL);
    natural.addAll(NYLIUM);
    natural.addAll(SAPLINGS.keySet());
    return natural;
  }
}
