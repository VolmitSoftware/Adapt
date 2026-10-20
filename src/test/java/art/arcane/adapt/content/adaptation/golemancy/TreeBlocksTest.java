package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeBlocksTest {
  @Test
  void logsWoodAndStrippedWoodOfEveryOverworldSpeciesAreTrunkWood() {
    for (Material wood : List.of(Material.OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD,
        Material.PALE_OAK_WOOD, Material.CHERRY_WOOD, Material.MANGROVE_WOOD)) {
      assertThat(TreeBlocks.trunk(wood, false)).as("%s", wood).isTrue();
      assertThat(TreeBlocks.building(wood)).as("%s", wood).isFalse();
    }
    assertThat(TreeBlocks.trunk(Material.CRIMSON_STEM, false)).isFalse();
    assertThat(TreeBlocks.trunk(Material.CRIMSON_STEM, true)).isTrue();
    assertThat(TreeBlocks.trunk(Material.MUSHROOM_STEM, true)).isFalse();
    assertThat(TreeBlocks.trunk(Material.OAK_PLANKS, false)).isFalse();
  }

  @Test
  void everySpeciesReplantsItsOwnSaplingFromAnyOfItsWoodBlocks() {
    assertThat(TreeBlocks.sapling(Material.OAK_WOOD)).isEqualTo(Material.OAK_SAPLING);
    assertThat(TreeBlocks.sapling(Material.STRIPPED_PALE_OAK_LOG)).isEqualTo(Material.PALE_OAK_SAPLING);
    assertThat(TreeBlocks.sapling(Material.CHERRY_WOOD)).isEqualTo(Material.CHERRY_SAPLING);
    assertThat(TreeBlocks.sapling(Material.MANGROVE_WOOD)).isEqualTo(Material.MANGROVE_PROPAGULE);
    assertThat(TreeBlocks.sapling(Material.WARPED_STEM)).isEqualTo(Material.WARPED_FUNGUS);
    assertThat(TreeBlocks.growsAlone(Material.DARK_OAK_SAPLING)).isFalse();
    assertThat(TreeBlocks.growsAlone(Material.PALE_OAK_SAPLING)).isFalse();
    assertThat(TreeBlocks.growsAlone(Material.SPRUCE_SAPLING)).isTrue();
  }

  @Test
  void leavesOfEveryKindAreLeaves() {
    for (Material leaves : List.of(Material.OAK_LEAVES, Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.CHERRY_LEAVES,
        Material.PALE_OAK_LEAVES)) {
      assertThat(TreeBlocks.leaves(leaves)).as("%s", leaves).isTrue();
    }
    assertThat(TreeBlocks.leaves(Material.LEAF_LITTER)).isFalse();
  }

  @Test
  void buildingBlocksAreTheOnesPlayersBuildWith() {
    for (Material built : List.of(Material.OAK_PLANKS, Material.GLASS, Material.GLASS_PANE, Material.IRON_DOOR, Material.OAK_TRAPDOOR,
        Material.BRICK_STAIRS, Material.OAK_SLAB, Material.NETHER_BRICK_FENCE, Material.ACACIA_FENCE_GATE, Material.MOSSY_STONE_BRICK_WALL,
        Material.SOUL_TORCH, Material.REDSTONE_WALL_TORCH, Material.SOUL_LANTERN, Material.BLUE_WOOL, Material.GREEN_CARPET,
        Material.ORANGE_CONCRETE, Material.MUD_BRICKS, Material.DEEPSLATE_TILES, Material.COBBLESTONE, Material.TRAPPED_CHEST,
        Material.BARREL, Material.SPRUCE_HANGING_SIGN, Material.WHITE_BANNER, Material.CANDLE, Material.IRON_BARS, Material.RAIL,
        Material.FLOWER_POT, Material.POTTED_POPPY, Material.SMOOTH_STONE, Material.CUT_SANDSTONE, Material.CHISELED_STONE_BRICKS,
        Material.BLACK_GLAZED_TERRACOTTA, Material.BEEHIVE)) {
      assertThat(TreeBlocks.building(built)).as("%s", built).isTrue();
    }
    for (Material natural : List.of(Material.MOSS_CARPET, Material.PALE_MOSS_CARPET, Material.SMOOTH_BASALT, Material.TERRACOTTA,
        Material.CYAN_TERRACOTTA, Material.MOSSY_COBBLESTONE, Material.BEE_NEST, Material.VINE, Material.COCOA, Material.SNOW,
        Material.BROWN_MUSHROOM_BLOCK, Material.STONE, Material.COAL_ORE, Material.LEAF_LITTER, Material.DIRT, Material.OAK_LEAVES)) {
      assertThat(TreeBlocks.building(natural)).as("%s", natural).isFalse();
    }
  }

  @Test
  void trunksStandOnTheGroundButSaplingsOnlyOnSoil() {
    for (Material ground : List.of(Material.GRASS_BLOCK, Material.STONE, Material.ANDESITE, Material.GRAVEL, Material.SAND,
        Material.SNOW_BLOCK, Material.PACKED_ICE, Material.CALCITE, Material.RED_TERRACOTTA)) {
      assertThat(TreeBlocks.ground(ground)).as("%s", ground).isTrue();
    }
    assertThat(TreeBlocks.ground(Material.MANGROVE_ROOTS)).isFalse();
    assertThat(TreeBlocks.ground(Material.OAK_PLANKS)).isFalse();
    assertThat(TreeBlocks.soil(Material.GRASS_BLOCK)).isTrue();
    assertThat(TreeBlocks.soil(Material.STONE)).isFalse();
  }
}
