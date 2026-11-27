package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeBlocksTest {
  @Test
  void logsWoodAndStrippedWoodOfEveryOverworldSpeciesAreTrunkWood() {
    for (Id wood : List.of(Id.minecraft("oak_log"), Id.minecraft("oak_wood"), Id.minecraft("stripped_oak_log"), Id.minecraft("stripped_dark_oak_wood"),
        Id.minecraft("pale_oak_wood"), Id.minecraft("cherry_wood"), Id.minecraft("mangrove_wood"))) {
      assertThat(TreeBlocks.trunk(wood, false)).as("%s", wood).isTrue();
      assertThat(TreeBlocks.building(wood)).as("%s", wood).isFalse();
    }
    assertThat(TreeBlocks.trunk(Id.minecraft("crimson_stem"), false)).isFalse();
    assertThat(TreeBlocks.trunk(Id.minecraft("crimson_stem"), true)).isTrue();
    assertThat(TreeBlocks.trunk(Id.minecraft("mushroom_stem"), true)).isFalse();
    assertThat(TreeBlocks.trunk(Id.minecraft("oak_planks"), false)).isFalse();
  }

  @Test
  void everySpeciesReplantsItsOwnSaplingFromAnyOfItsWoodBlocks() {
    assertThat(TreeBlocks.sapling(Id.minecraft("oak_wood"))).isEqualTo(Id.minecraft("oak_sapling"));
    assertThat(TreeBlocks.sapling(Id.minecraft("stripped_pale_oak_log"))).isEqualTo(Id.minecraft("pale_oak_sapling"));
    assertThat(TreeBlocks.sapling(Id.minecraft("cherry_wood"))).isEqualTo(Id.minecraft("cherry_sapling"));
    assertThat(TreeBlocks.sapling(Id.minecraft("mangrove_wood"))).isEqualTo(Id.minecraft("mangrove_propagule"));
    assertThat(TreeBlocks.sapling(Id.minecraft("warped_stem"))).isEqualTo(Id.minecraft("warped_fungus"));
    assertThat(TreeBlocks.growsAlone(Id.minecraft("dark_oak_sapling"))).isFalse();
    assertThat(TreeBlocks.growsAlone(Id.minecraft("pale_oak_sapling"))).isFalse();
    assertThat(TreeBlocks.growsAlone(Id.minecraft("spruce_sapling"))).isTrue();
  }

  @Test
  void leavesOfEveryKindAreLeaves() {
    for (Id leaves : List.of(Id.minecraft("oak_leaves"), Id.minecraft("azalea_leaves"), Id.minecraft("flowering_azalea_leaves"), Id.minecraft("cherry_leaves"),
        Id.minecraft("pale_oak_leaves"))) {
      assertThat(TreeBlocks.leaves(leaves)).as("%s", leaves).isTrue();
    }
    assertThat(TreeBlocks.leaves(Id.minecraft("leaf_litter"))).isFalse();
  }

  @Test
  void buildingBlocksAreTheOnesPlayersBuildWith() {
    for (Id built : List.of(Id.minecraft("oak_planks"), Id.minecraft("glass"), Id.minecraft("glass_pane"), Id.minecraft("iron_door"), Id.minecraft("oak_trapdoor"),
        Id.minecraft("brick_stairs"), Id.minecraft("oak_slab"), Id.minecraft("nether_brick_fence"), Id.minecraft("acacia_fence_gate"), Id.minecraft("mossy_stone_brick_wall"),
        Id.minecraft("soul_torch"), Id.minecraft("redstone_wall_torch"), Id.minecraft("soul_lantern"), Id.minecraft("blue_wool"), Id.minecraft("green_carpet"),
        Id.minecraft("orange_concrete"), Id.minecraft("mud_bricks"), Id.minecraft("deepslate_tiles"), Id.minecraft("cobblestone"), Id.minecraft("trapped_chest"),
        Id.minecraft("barrel"), Id.minecraft("spruce_hanging_sign"), Id.minecraft("white_banner"), Id.minecraft("candle"), Id.minecraft("iron_bars"), Id.minecraft("rail"),
        Id.minecraft("flower_pot"), Id.minecraft("potted_poppy"), Id.minecraft("smooth_stone"), Id.minecraft("cut_sandstone"), Id.minecraft("chiseled_stone_bricks"),
        Id.minecraft("black_glazed_terracotta"), Id.minecraft("beehive"))) {
      assertThat(TreeBlocks.building(built)).as("%s", built).isTrue();
    }
    for (Id natural : List.of(Id.minecraft("moss_carpet"), Id.minecraft("pale_moss_carpet"), Id.minecraft("smooth_basalt"), Id.minecraft("terracotta"),
        Id.minecraft("cyan_terracotta"), Id.minecraft("mossy_cobblestone"), Id.minecraft("bee_nest"), Id.minecraft("vine"), Id.minecraft("cocoa"), Id.minecraft("snow"),
        Id.minecraft("brown_mushroom_block"), Id.minecraft("stone"), Id.minecraft("coal_ore"), Id.minecraft("leaf_litter"), Id.minecraft("dirt"), Id.minecraft("oak_leaves"))) {
      assertThat(TreeBlocks.building(natural)).as("%s", natural).isFalse();
    }
  }

  @Test
  void trunksStandOnTheGroundButSaplingsOnlyOnSoil() {
    for (Id ground : List.of(Id.minecraft("grass_block"), Id.minecraft("stone"), Id.minecraft("andesite"), Id.minecraft("gravel"), Id.minecraft("sand"),
        Id.minecraft("snow_block"), Id.minecraft("packed_ice"), Id.minecraft("calcite"), Id.minecraft("red_terracotta"))) {
      assertThat(TreeBlocks.ground(ground)).as("%s", ground).isTrue();
    }
    assertThat(TreeBlocks.ground(Id.minecraft("mangrove_roots"))).isFalse();
    assertThat(TreeBlocks.ground(Id.minecraft("oak_planks"))).isFalse();
    assertThat(TreeBlocks.soil(Id.minecraft("grass_block"))).isTrue();
    assertThat(TreeBlocks.soil(Id.minecraft("stone"))).isFalse();
  }
}
