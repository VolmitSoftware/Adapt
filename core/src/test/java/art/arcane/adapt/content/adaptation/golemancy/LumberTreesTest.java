package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LumberTreesTest {
  private static final LumberTrees.Reach SMALL = new LumberTrees.Reach(false, false, 64);
  private static final LumberTrees.Reach MEGA = new LumberTrees.Reach(true, false, 256);
  private static final LumberTrees.Reach NETHER = new LumberTrees.Reach(true, true, 256);

  @Test
  void anOakOnDirtIsANaturalTreeFelledFromTheTopDown() {
    LumberTrees.Survey survey = LumberTrees.survey(oak(), 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    LumberTrees.Tree tree = survey.tree();
    assertThat(tree.species()).isEqualTo(Id.minecraft("oak_log"));
    assertThat(tree.logs()).isEqualTo(5);
    assertThat(position(tree.base())).isEqualTo("0,64,0");
    assertThat(tree.bases()).hasSize(1);
    assertThat(tree.parts()).extracting(LumberTrees.Part::y).containsExactly(68, 67, 66, 65, 64);
    assertThat(tree.parts()).extracting(LumberTrees.Part::type).containsOnly(Id.minecraft("oak_log"));
    assertThat(tree.nether()).isFalse();
  }

  @Test
  void birchSpruceAndCherryTreesAreNatural() {
    TreeGrid birch = ground(Id.minecraft("grass_block")).column(0, 0, 64, 69, Id.minecraft("birch_log"))
        .canopy(0, 67, 68, 0, 2, Id.minecraft("birch_leaves")).canopy(0, 69, 70, 0, 1, Id.minecraft("birch_leaves"));
    TreeGrid spruce = ground(Id.minecraft("podzol")).column(0, 0, 64, 71, Id.minecraft("spruce_log"))
        .canopy(0, 66, 69, 0, 2, Id.minecraft("spruce_leaves")).canopy(0, 70, 72, 0, 1, Id.minecraft("spruce_leaves")).set(1, 64, 0, Id.minecraft("fern"));
    TreeGrid cherry = ground(Id.minecraft("grass_block")).column(0, 0, 64, 67, Id.minecraft("cherry_log"))
        .set(1, 67, 0, Id.minecraft("cherry_log")).set(2, 67, 0, Id.minecraft("cherry_log")).set(2, 68, 0, Id.minecraft("cherry_log"))
        .canopy(2, 69, 70, 0, 2, Id.minecraft("cherry_leaves")).set(-1, 64, 0, Id.minecraft("pink_petals"));

    assertThat(verdictAndLogs(birch, SMALL)).isEqualTo("NATURAL 6");
    assertThat(verdictAndLogs(spruce, SMALL)).isEqualTo("NATURAL 8");
    assertThat(verdictAndLogs(cherry, SMALL)).isEqualTo("NATURAL 7");
  }

  @Test
  void acaciaBranchesThatOnlyTouchAtCornersStayInTheTree() {
    TreeGrid acacia = ground(Id.minecraft("grass_block")).column(0, 0, 64, 65, Id.minecraft("acacia_log"))
        .set(1, 66, 1, Id.minecraft("acacia_log")).set(2, 67, 2, Id.minecraft("acacia_log")).set(-1, 66, -1, Id.minecraft("acacia_log"))
        .canopy(2, 68, 68, 2, 2, Id.minecraft("acacia_leaves")).canopy(-1, 67, 67, -1, 1, Id.minecraft("acacia_leaves"));

    LumberTrees.Survey survey = LumberTrees.survey(acacia, 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().parts()).extracting(LumberTreesTest::position).containsExactly("2,67,2", "-1,66,-1", "1,66,1", "0,65,0", "0,64,0");
  }

  @Test
  void aPoplarWithFlaredRootsWoodRingsAndCrookedBranchesIsOneNaturalTree() {
    TreeGrid poplar = poplar(Id.minecraft("birch_log"), Id.minecraft("birch_wood"), Id.minecraft("birch_leaves"));

    LumberTrees.Survey survey = LumberTrees.survey(poplar, 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    LumberTrees.Tree tree = survey.tree();
    assertThat(tree.logs()).isEqualTo(17);
    assertThat(tree.bases()).extracting(LumberTreesTest::position).containsExactly("0,64,0");
    assertThat(tree.parts()).extracting(LumberTreesTest::position)
        .contains("1,64,0", "1,65,0", "0,64,1", "-1,64,1", "0,66,0", "0,70,0", "1,73,1", "2,74,1", "-1,73,-1", "-1,74,0");
    assertThat(tree.parts()).extracting(LumberTrees.Part::y).isSortedAccordingTo((first, second) -> Integer.compare(second, first));
    assertThat(tree.sapling()).isEqualTo(Id.minecraft("birch_sapling"));
    assertThat(LumberTrees.survey(poplar, -1, 64, 1, SMALL).tree().base()).isEqualTo(tree.base());
  }

  @Test
  void aFlaredRootBesideItsTrunkIsNoCandidate() {
    TreeGrid poplar = poplar(Id.minecraft("birch_log"), Id.minecraft("birch_wood"), Id.minecraft("birch_leaves"));

    assertThat(LumberTrees.candidate(poplar, 0, 64, 0, false)).isTrue();
    assertThat(LumberTrees.candidate(poplar, 1, 64, 0, false)).isFalse();
    assertThat(LumberTrees.candidate(poplar, 0, 64, 1, false)).isFalse();
  }

  @Test
  void aTaprootTreeStandsOnTheTipOfItsRoot() {
    TreeGrid taproot = poplar(Id.minecraft("birch_log"), Id.minecraft("birch_wood"), Id.minecraft("birch_leaves")).ground(-5, -5, 5, 5, 61, Id.minecraft("dirt"))
        .set(0, 63, 0, Id.minecraft("birch_log")).set(0, 62, 0, Id.minecraft("birch_log"));

    LumberTrees.Survey survey = LumberTrees.survey(taproot, 0, 62, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().bases()).extracting(LumberTreesTest::position).containsExactly("0,62,0");
    assertThat(survey.tree().logs()).isEqualTo(19);
  }

  @Test
  void poplarLogsLeavesAndSaplingsAreTreeBlocksWhereTheServerHasThem() {
    Id log = known("poplar_log");
    Id wood = known("poplar_wood");
    Id leaves = known("yellow_poplar_leaves");
    assumeTrue(log != null && wood != null && leaves != null, "this platform has no poplars");

    LumberTrees.Survey survey = LumberTrees.survey(poplar(log, wood, leaves), 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().logs()).isEqualTo(17);
    assertThat(survey.tree().sapling()).isEqualTo(known("poplar_sapling"));
  }

  @Test
  void woodBlocksStrippedWoodAndOtherSpeciesInOneCrownAreFelledAsOneTree() {
    TreeGrid mixed = ground(Id.minecraft("stone")).set(0, 63, 0, Id.minecraft("andesite")).column(0, 0, 64, 70, Id.minecraft("spruce_wood"))
        .set(1, 71, 1, Id.minecraft("dark_oak_wood")).set(2, 72, 2, Id.minecraft("dark_oak_wood"))
        .set(-1, 71, -1, Id.minecraft("stripped_dark_oak_wood")).set(-2, 72, -1, Id.minecraft("stripped_spruce_wood"))
        .canopy(0, 69, 73, 0, 2, Id.minecraft("spruce_leaves"));

    LumberTrees.Survey survey = LumberTrees.survey(mixed, 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().logs()).isEqualTo(11);
    assertThat(survey.tree().parts()).extracting(LumberTreesTest::position).contains("2,72,2", "-2,72,-1", "1,71,1", "-1,71,-1");
    assertThat(survey.tree().sapling()).isEqualTo(Id.minecraft("spruce_sapling"));
  }

  @Test
  void treesStandOnTheGroundTheirWorldGeneratorUses() {
    for (Id ground : List.of(Id.minecraft("coarse_dirt"), Id.minecraft("stone"), Id.minecraft("andesite"), Id.minecraft("gravel"), Id.minecraft("snow_block"),
        Id.minecraft("cyan_terracotta"), Id.minecraft("calcite"))) {
      assertThat(LumberTrees.survey(oak().set(0, 63, 0, ground), 0, 64, 0, SMALL).verdict()).as("oak on %s", ground)
          .isEqualTo(LumberTrees.Verdict.NATURAL);
    }
  }

  @Test
  void jungleTrunksWithCocoaVinesAndBeeNestsAreNatural() {
    TreeGrid jungle = ground(Id.minecraft("grass_block")).column(0, 0, 64, 73, Id.minecraft("jungle_log"))
        .set(1, 66, 0, Id.minecraft("cocoa")).set(0, 67, -1, Id.minecraft("vine")).set(-1, 70, 0, Id.minecraft("bee_nest"))
        .canopy(0, 72, 74, 0, 2, Id.minecraft("jungle_leaves"));

    assertThat(verdictAndLogs(jungle, SMALL)).isEqualTo("NATURAL 10");
  }

  @Test
  void logsTouchingTheGroundPlantsAndRocksAroundTreesStayTrees() {
    for (Id near : List.of(Id.minecraft("dirt"), Id.minecraft("coarse_dirt"), Id.minecraft("podzol"), Id.minecraft("vine"), Id.minecraft("cocoa"), Id.minecraft("snow"),
        Id.minecraft("moss_carpet"), Id.minecraft("brown_mushroom"), Id.minecraft("red_mushroom_block"), Id.minecraft("bee_nest"), Id.minecraft("stone"), Id.minecraft("andesite"),
        Id.minecraft("coal_ore"), Id.minecraft("gravel"), Id.minecraft("cyan_terracotta"), Id.minecraft("leaf_litter"), Id.minecraft("bush"), Id.minecraft("sweet_berry_bush"),
        Id.minecraft("mossy_cobblestone"), Id.minecraft("calcite"), Id.minecraft("tuff"), Id.minecraft("packed_ice"))) {
      TreeGrid tree = oak().set(1, 65, 0, near);

      assertThat(LumberTrees.survey(tree, 0, 64, 0, SMALL).verdict()).as("log touching %s", near).isEqualTo(LumberTrees.Verdict.NATURAL);
    }
  }

  @Test
  void megaTreesNeedTheMegaLevelAndAreFelledAsOneTree() {
    TreeGrid spruce = ground(Id.minecraft("podzol"));
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        spruce.column(x, z, 64, 75, Id.minecraft("spruce_log"));
      }
    }
    spruce.canopy(0, 70, 76, 0, 3, Id.minecraft("spruce_leaves"));

    assertThat(LumberTrees.survey(spruce, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    LumberTrees.Survey survey = LumberTrees.survey(spruce, 0, 64, 0, MEGA);
    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().logs()).isEqualTo(48);
    assertThat(survey.tree().bases()).extracting(LumberTreesTest::position).containsExactlyInAnyOrder("0,64,0", "1,64,0", "0,64,1", "1,64,1");
    assertThat(position(survey.tree().base())).isEqualTo("0,64,0");
    assertThat(LumberTrees.survey(spruce, 1, 64, 1, MEGA).tree().base()).isEqualTo(survey.tree().base());
  }

  @Test
  void aDarkOakWithABranchIsAMegaTree() {
    TreeGrid darkOak = ground(Id.minecraft("grass_block"));
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        darkOak.column(x, z, 64, 69, Id.minecraft("dark_oak_log"));
      }
    }
    darkOak.set(2, 70, 2, Id.minecraft("dark_oak_log")).canopy(1, 69, 71, 1, 3, Id.minecraft("dark_oak_leaves")).set(-1, 64, 0, Id.minecraft("brown_mushroom"));

    assertThat(LumberTrees.survey(darkOak, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    assertThat(verdictAndLogs(darkOak, MEGA)).isEqualTo("NATURAL 25");
    assertThat(LumberTrees.survey(darkOak, 0, 64, 0, MEGA).tree().parts().get(0)).isEqualTo(new LumberTrees.Part(2, 70, 2, Id.minecraft("dark_oak_log")));
  }

  @Test
  void onlyTheNorthWestBaseOfAWideTrunkIsACandidate() {
    TreeGrid spruce = ground(Id.minecraft("podzol"));
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        spruce.column(x, z, 64, 70, Id.minecraft("spruce_log"));
      }
    }

    assertThat(LumberTrees.candidate(spruce, 0, 64, 0, false)).isTrue();
    assertThat(LumberTrees.candidate(spruce, 1, 64, 0, false)).isFalse();
    assertThat(LumberTrees.candidate(spruce, 0, 64, 1, false)).isFalse();
    assertThat(LumberTrees.candidate(spruce, 1, 64, 1, false)).isFalse();
    assertThat(LumberTrees.candidate(spruce, 0, 65, 0, false)).isFalse();
    assertThat(LumberTrees.candidate(spruce, 0, 63, 0, false)).isFalse();
  }

  @Test
  void theTallestColumnOfAWideTrunkIsItsBase() {
    TreeGrid darkOak = ground(Id.minecraft("grass_block"));
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        darkOak.column(x, z, 64, x == 1 ? 71 : 68, Id.minecraft("dark_oak_log"));
      }
    }
    darkOak.canopy(1, 70, 72, 1, 3, Id.minecraft("dark_oak_leaves"));

    assertThat(position(LumberTrees.survey(darkOak, 0, 64, 0, MEGA).tree().base())).isEqualTo("1,64,0");
    assertThat(LumberTrees.candidate(darkOak, 1, 64, 0, false)).isTrue();
    assertThat(LumberTrees.candidate(darkOak, 0, 64, 0, false)).isFalse();
    assertThat(LumberTrees.candidate(darkOak, 1, 64, 1, false)).isFalse();
  }

  @Test
  void mangrovesStandOnTheirOwnRootsAboveTheMud() {
    TreeGrid mangrove = mangrove();

    assertThat(LumberTrees.candidate(mangrove, 0, 64, 0, false)).isTrue();
    LumberTrees.Survey survey = LumberTrees.survey(mangrove, 0, 64, 0, SMALL);
    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().logs()).isEqualTo(5);
    assertThat(survey.tree().parts()).extracting(LumberTrees.Part::type).containsOnly(Id.minecraft("mangrove_log"));

    mangrove.stamp(0, 63, 0);
    assertThat(LumberTrees.candidate(mangrove, 0, 64, 0, false)).isTrue();
    assertThat(LumberTrees.survey(mangrove, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(LumberTrees.survey(mangrove, 0, 64, 0, SMALL).tree().sapling()).isEqualTo(Id.minecraft("mangrove_propagule"));
  }

  @Test
  void aMangroveLogStandingOnAnotherRootStaysPartOfItsTrunk() {
    TreeGrid mangrove = mangrove().set(1, 65, 0, Id.minecraft("mangrove_log"));

    LumberTrees.Survey survey = LumberTrees.survey(mangrove, 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().logs()).isEqualTo(6);
    assertThat(survey.tree().bases()).extracting(LumberTreesTest::position).containsExactly("0,64,0");
  }

  @Test
  void otherLogsNeverStandOnMangroveRoots() {
    TreeGrid oak = oak().set(0, 63, 0, Id.minecraft("mangrove_roots"));

    assertThat(LumberTrees.survey(oak, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
  }

  @Test
  void aStampedLogBesideANaturalTrunkIsRefused() {
    TreeGrid spruce = ground(Id.minecraft("podzol")).column(0, 0, 64, 71, Id.minecraft("spruce_log"))
        .canopy(0, 66, 69, 0, 2, Id.minecraft("spruce_leaves")).canopy(0, 70, 72, 0, 1, Id.minecraft("spruce_leaves"))
        .set(1, 64, 0, Id.minecraft("spruce_log")).stamp(1, 64, 0);
    TreeGrid oak = oak().set(1, 64, 0, Id.minecraft("oak_log")).stamp(1, 64, 0);

    assertThat(LumberTrees.survey(spruce, 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(spruce, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(oak, 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
  }

  @Test
  void aTrunkWiderThanOneBlockOfAnySpeciesIsAMegaTree() {
    TreeGrid oak = ground(Id.minecraft("grass_block"));
    TreeGrid corner = ground(Id.minecraft("podzol"));
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        oak.column(x, z, 64, 68, Id.minecraft("oak_wood"));
        if (x == 0 || z == 0) {
          corner.column(x, z, 64, 68, Id.minecraft("spruce_log"));
        }
      }
    }
    oak.canopy(0, 67, 69, 0, 2, Id.minecraft("oak_leaves"));
    corner.canopy(0, 67, 69, 0, 2, Id.minecraft("spruce_leaves"));

    assertThat(LumberTrees.survey(oak, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    assertThat(LumberTrees.survey(corner, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    assertThat(verdictAndLogs(oak, MEGA)).isEqualTo("NATURAL 20");
    assertThat(LumberTrees.survey(corner, 0, 64, 0, MEGA).tree().bases()).hasSize(3);
  }

  @Test
  void a2x2TrunkCountsAsPlantedOnlyWhenAllOrNoneOfItsBasesAreStamped() {
    assertThat(LumberTrees.survey(megaSpruce().stamp(1, 64, 1), 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(megaSpruce().stamp(0, 64, 0).stamp(1, 64, 0).stamp(0, 64, 1).stamp(1, 64, 1), 0, 64, 0, MEGA).verdict())
        .isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(LumberTrees.survey(megaSpruce(), 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void logsPlacedToCompleteA2x2AroundAPlantedTrunkAreNoMegaTree() {
    TreeGrid planted = ground(Id.minecraft("podzol")).column(0, 0, 64, 71, Id.minecraft("spruce_log")).stamp(0, 64, 0)
        .canopy(0, 66, 69, 0, 2, Id.minecraft("spruce_leaves")).canopy(0, 70, 72, 0, 1, Id.minecraft("spruce_leaves"));
    planted.set(1, 64, 0, Id.minecraft("spruce_log")).set(0, 64, 1, Id.minecraft("spruce_log")).set(1, 64, 1, Id.minecraft("spruce_log"))
        .stamp(1, 64, 0).stamp(0, 64, 1).stamp(1, 64, 1);

    assertThat(LumberTrees.survey(planted, 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(planted, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
  }

  @Test
  void placedStemsAroundAFungusAreRefusedButAThickNaturalStemIsNot() {
    TreeGrid placed = crimson().set(-1, 64, 0, Id.minecraft("crimson_stem")).stamp(-1, 64, 0);
    TreeGrid thick = crimson().column(-1, 0, 64, 65, Id.minecraft("crimson_stem"));

    TreeGrid allPlaced = crimson().set(-1, 64, 0, Id.minecraft("crimson_stem")).stamp(-1, 64, 0).stamp(0, 64, 0);
    TreeGrid planted = crimson().stamp(0, 64, 0);

    assertThat(LumberTrees.survey(placed, -1, 64, 0, NETHER).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(allPlaced, -1, 64, 0, NETHER).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(planted, 0, 64, 0, NETHER).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(verdictAndLogs(thick, -1, NETHER)).isEqualTo("NATURAL 8");
  }

  @Test
  void aFungusWhoseCapReachesIntoAnotherRegionIsLeftStanding() {
    assertThat(LumberTrees.survey(crimson().foreign(3, 69, 0), 0, 64, 0, NETHER).verdict()).isEqualTo(LumberTrees.Verdict.UNOWNED);
  }

  @Test
  void azaleaTreesReplantAzaleasAndFloweringOnesFloweringAzaleas() {
    TreeGrid azalea = new TreeGrid().ground(-5, -5, 5, 5, 63, Id.minecraft("rooted_dirt")).column(0, 0, 64, 67, Id.minecraft("oak_log"))
        .canopy(0, 66, 68, 0, 2, Id.minecraft("azalea_leaves"));
    TreeGrid flowering = new TreeGrid().ground(-5, -5, 5, 5, 63, Id.minecraft("rooted_dirt")).column(0, 0, 64, 67, Id.minecraft("oak_log"))
        .canopy(0, 66, 68, 0, 2, Id.minecraft("flowering_azalea_leaves")).set(1, 68, 1, Id.minecraft("azalea_leaves"));

    assertThat(LumberTrees.survey(azalea, 0, 64, 0, SMALL).tree().sapling()).isEqualTo(Id.minecraft("azalea"));
    assertThat(LumberTrees.survey(flowering, 0, 64, 0, SMALL).tree().sapling()).isEqualTo(Id.minecraft("flowering_azalea"));
    assertThat(LumberTrees.survey(oak(), 0, 64, 0, SMALL).tree().sapling()).isEqualTo(Id.minecraft("oak_sapling"));
  }

  @Test
  void rocksGourdsAndBambooBesideATrunkAreNatural() {
    TreeGrid tree = oak().set(1, 64, 0, Id.minecraft("mossy_cobblestone")).set(-1, 64, 0, Id.minecraft("pumpkin")).set(0, 64, 1, Id.minecraft("melon"))
        .set(0, 64, -1, Id.minecraft("bamboo")).set(1, 65, 0, Id.minecraft("bamboo"));

    assertThat(LumberTrees.survey(tree, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void netherFungiNeedTheirLevelAndBringTheirWartAndShroomlights() {
    TreeGrid fungus = crimson();

    assertThat(LumberTrees.candidate(fungus, 0, 64, 0, false)).isFalse();
    assertThat(LumberTrees.survey(fungus, 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
    assertThat(LumberTrees.candidate(fungus, 0, 64, 0, true)).isTrue();
    LumberTrees.Survey survey = LumberTrees.survey(fungus, 0, 64, 0, NETHER);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    LumberTrees.Tree tree = survey.tree();
    assertThat(tree.nether()).isTrue();
    assertThat(tree.logs()).isEqualTo(6);
    List<String> parts = tree.parts().stream().map(LumberTreesTest::position).toList();
    assertThat(parts).contains("1,68,1", "3,68,0").doesNotContain("4,68,0", "2,69,2", "2,66,2", "1,64,0");
    assertThat(tree.parts()).contains(new LumberTrees.Part(1, 68, 1, Id.minecraft("shroomlight")));
    assertThat(tree.parts().get(0).y()).isEqualTo(70);
    assertThat(tree.parts()).extracting(LumberTrees.Part::y).isSortedAccordingTo((first, second) -> Integer.compare(second, first));
    int stem = parts.indexOf("0,69,0");
    assertThat(tree.parts().subList(stem + 1, tree.parts().size())).noneMatch(part -> part.y() == 69 && !part.type().equals(Id.minecraft("crimson_stem")));
  }

  @Test
  void logsTouchingBuildingBlocksAreNeverCut() {
    for (Id built : List.of(Id.minecraft("oak_planks"), Id.minecraft("glass"), Id.minecraft("white_stained_glass_pane"), Id.minecraft("oak_door"),
        Id.minecraft("spruce_trapdoor"), Id.minecraft("oak_stairs"), Id.minecraft("stone_slab"), Id.minecraft("oak_fence"), Id.minecraft("oak_fence_gate"),
        Id.minecraft("cobblestone_wall"), Id.minecraft("torch"), Id.minecraft("lantern"), Id.minecraft("white_wool"), Id.minecraft("red_carpet"), Id.minecraft("white_concrete"),
        Id.minecraft("bricks"), Id.minecraft("stone_bricks"), Id.minecraft("cobblestone"), Id.minecraft("chest"), Id.minecraft("oak_sign"), Id.minecraft("red_bed"),
        Id.minecraft("crafting_table"), Id.minecraft("stone_button"), Id.minecraft("ladder"), Id.minecraft("bookshelf"), Id.minecraft("polished_andesite"))) {
      TreeGrid house = oak().set(1, 65, 0, built);

      assertThat(LumberTrees.survey(house, 0, 64, 0, SMALL).verdict()).as("log touching %s", built).isEqualTo(LumberTrees.Verdict.BUILT);
    }
  }

  @Test
  void stampedLeavesTouchingALogMarkABuild() {
    TreeGrid roof = oak().stamp(1, 66, 0);

    assertThat(LumberTrees.survey(roof, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.BUILT);
  }

  @Test
  void aLogCabinOfPillarsAndPlanksIsNeverATree() {
    TreeGrid cabin = ground(Id.minecraft("grass_block"));
    for (int x = 0; x <= 3; x++) {
      for (int z = 0; z <= 3; z++) {
        boolean corner = (x == 0 || x == 3) && (z == 0 || z == 3);
        boolean wall = x == 0 || x == 3 || z == 0 || z == 3;
        if (wall) {
          cabin.column(x, z, 64, 66, corner ? Id.minecraft("spruce_log") : Id.minecraft("spruce_planks"));
        }
        cabin.set(x, 67, z, Id.minecraft("spruce_leaves")).stamp(x, 67, z);
      }
    }

    assertThat(LumberTrees.survey(cabin, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.BUILT);
    assertThat(LumberTrees.survey(cabin, 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.BUILT);
  }

  @Test
  void aPlacedStampOnAnUpperLogRejectsTheTreeButTheSaplingSpotDoesNot() {
    assertThat(LumberTrees.survey(oak().stamp(0, 66, 0), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(oak().stamp(0, 64, 0), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void aTreeNeedsUnstampedLeavesWithinTwoBlocksOfItsTopLogs() {
    TreeGrid pillar = ground(Id.minecraft("grass_block")).column(0, 0, 64, 68, Id.minecraft("oak_log"));
    TreeGrid distant = ground(Id.minecraft("grass_block")).column(0, 0, 64, 68, Id.minecraft("oak_log")).canopy(3, 72, 72, 0, 0, Id.minecraft("oak_leaves"));
    TreeGrid decorated = ground(Id.minecraft("grass_block")).column(0, 0, 64, 68, Id.minecraft("oak_log"))
        .set(2, 69, 0, Id.minecraft("oak_leaves")).stamp(2, 69, 0);
    TreeGrid near = ground(Id.minecraft("grass_block")).column(0, 0, 64, 68, Id.minecraft("oak_log")).set(2, 70, 2, Id.minecraft("oak_leaves"));

    assertThat(LumberTrees.survey(pillar, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NO_LEAVES);
    assertThat(LumberTrees.survey(distant, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NO_LEAVES);
    assertThat(LumberTrees.survey(decorated, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NO_LEAVES);
    assertThat(LumberTrees.survey(near, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void treesWithMoreLogsThanTheCapAreLeftStandingBeforeTheyAreReadWhole() {
    TreeGrid tall = ground(Id.minecraft("grass_block")).column(0, 0, 64, 263, Id.minecraft("oak_log")).canopy(0, 262, 264, 0, 1, Id.minecraft("oak_leaves"));

    assertThat(LumberTrees.survey(oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 4)).verdict()).isEqualTo(LumberTrees.Verdict.TOO_BIG);
    assertThat(LumberTrees.survey(oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 5)).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(LumberTrees.survey(tall, 0, 64, 0, new LumberTrees.Reach(false, false, 8)).verdict()).isEqualTo(LumberTrees.Verdict.TOO_BIG);
    assertThat(tall.reads()).isLessThan(300);
  }

  @Test
  void aTreeReachingIntoAnotherRegionIsLeftStanding() {
    assertThat(LumberTrees.survey(oak().foreign(1, 66, 0), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.UNOWNED);
    assertThat(LumberTrees.survey(oak().foreign(1, 67, 1), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.UNOWNED);
    assertThat(LumberTrees.candidate(oak().foreign(0, 63, 0), 0, 64, 0, false)).isFalse();
  }

  @Test
  void aLoneLogALogOffTheGroundOrANonLogIsNoTree() {
    TreeGrid lone = ground(Id.minecraft("grass_block")).set(0, 64, 0, Id.minecraft("oak_log")).canopy(0, 65, 65, 0, 1, Id.minecraft("oak_leaves"));
    TreeGrid water = oak().set(0, 63, 0, Id.minecraft("water"));

    assertThat(LumberTrees.survey(lone, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
    assertThat(LumberTrees.survey(water, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
    assertThat(LumberTrees.survey(oak(), 0, 65, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
    assertThat(LumberTrees.survey(oak(), 0, 63, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
  }

  @Test
  void treesJoinedByRootsOrBranchesAreFelledTogetherFromTheMegaLevel() {
    TreeGrid roots = joinedPoplars();
    TreeGrid branch = oak().column(4, 0, 64, 68, Id.minecraft("oak_log")).canopy(4, 66, 67, 0, 1, Id.minecraft("oak_leaves"))
        .set(1, 68, 0, Id.minecraft("oak_log")).set(2, 68, 0, Id.minecraft("oak_log")).set(3, 68, 0, Id.minecraft("oak_log"));

    assertThat(LumberTrees.survey(roots, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    LumberTrees.Survey survey = LumberTrees.survey(roots, 0, 64, 0, MEGA);
    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().bases()).extracting(LumberTreesTest::position).containsExactly("0,64,0", "0,64,3");
    assertThat(survey.tree().logs()).isEqualTo(16);
    assertThat(LumberTrees.survey(branch, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    assertThat(verdictAndLogs(branch, MEGA)).isEqualTo("NATURAL 13");
  }

  @Test
  void anotherSpeciesTouchingTheTrunkJoinsTheTree() {
    TreeGrid mixed = oak().column(1, 0, 64, 66, Id.minecraft("birch_log"));

    assertThat(LumberTrees.survey(mixed, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    LumberTrees.Survey survey = LumberTrees.survey(mixed, 0, 64, 0, MEGA);
    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().parts()).extracting(LumberTrees.Part::type).contains(Id.minecraft("oak_log"), Id.minecraft("birch_log"));
    assertThat(survey.tree().logs()).isEqualTo(8);
  }

  @Test
  void aLogStaysIntactUntilSomethingIsBuiltAgainstIt() {
    TreeGrid tree = oak();
    LumberTrees.Part log = new LumberTrees.Part(0, 66, 0, Id.minecraft("oak_log"));

    assertThat(LumberTrees.intact(tree, log)).isEqualTo(LumberTrees.Verdict.NATURAL);
    tree.set(0, 66, 1, Id.minecraft("oak_planks"));
    assertThat(LumberTrees.intact(tree, log)).isEqualTo(LumberTrees.Verdict.BUILT);
    assertThat(LumberTrees.intact(oak().stamp(0, 66, 0), log)).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.intact(oak().stamp(0, 64, 0), new LumberTrees.Part(0, 64, 0, Id.minecraft("oak_log")))).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(LumberTrees.intact(oak().set(0, 66, 1, Id.minecraft("spruce_wood")), log)).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(LumberTrees.intact(oak().stamp(0, 66, 1).set(0, 66, 1, Id.minecraft("oak_leaves")), log)).isEqualTo(LumberTrees.Verdict.BUILT);
  }

  @Test
  void leafSweepCandidatesAreTheUnstampedLeavesWithinSixStepsOfTheFelledLogs() {
    TreeGrid tree = oak();
    for (int x = 3; x <= 9; x++) {
      tree.set(x, 66, 0, Id.minecraft("oak_leaves"));
    }
    List<LumberTrees.Part> logs = LumberTrees.survey(tree, 0, 64, 0, SMALL).tree().parts();
    for (LumberTrees.Part log : logs) {
      tree.set(log.x(), log.y(), log.z(), Id.minecraft("air"));
    }
    tree.loosen(1, 66, 0).loosen(1, 67, 0).stamp(1, 67, 0);

    List<String> candidates = new ArrayList<>();
    for (LumberTrees.Part leaf : LumberTrees.leafCandidates(tree, logs)) {
      candidates.add(position(leaf));
    }

    assertThat(candidates).hasSize(68).contains("6,66,0").doesNotContain("7,66,0", "1,67,0");
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(1, 66, 0, Id.minecraft("oak_leaves")))).isTrue();
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(2, 66, 0, Id.minecraft("oak_leaves")))).isFalse();
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(1, 67, 0, Id.minecraft("oak_leaves")))).isFalse();
    tree.set(1, 66, 0, Id.minecraft("air"));
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(1, 66, 0, Id.minecraft("oak_leaves")))).isFalse();
  }

  @Test
  void aHouseIsRejectedBeforeTheWholeFrameIsRead() {
    TreeGrid house = oak().set(0, 64, 1, Id.minecraft("oak_planks"));

    assertThat(LumberTrees.survey(house, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.BUILT);
    assertThat(house.reads()).isLessThan(40);
  }

  static TreeGrid oak() {
    return ground(Id.minecraft("grass_block")).set(0, 63, 0, Id.minecraft("dirt")).column(0, 0, 64, 68, Id.minecraft("oak_log"))
        .canopy(0, 66, 67, 0, 2, Id.minecraft("oak_leaves")).canopy(0, 68, 69, 0, 1, Id.minecraft("oak_leaves"));
  }

  static TreeGrid poplar(Id log, Id wood, Id leaves) {
    return new TreeGrid().ground(-5, -5, 5, 5, 62, Id.minecraft("dirt")).ground(-5, -5, 5, 5, 63, Id.minecraft("coarse_dirt"))
        .column(0, 0, 64, 72, log).set(0, 66, 0, wood).set(0, 70, 0, wood)
        .set(1, 64, 0, log).set(1, 65, 0, log).set(0, 64, 1, log).set(-1, 64, 1, wood)
        .set(1, 73, 1, log).set(2, 74, 1, log).set(-1, 73, -1, wood).set(-1, 74, 0, log)
        .canopy(0, 67, 75, 0, 1, leaves).set(0, 76, 0, leaves)
        .set(-1, 65, 0, Id.minecraft("brown_mushroom")).set(0, 64, -1, Id.minecraft("leaf_litter"));
  }

  static TreeGrid joinedPoplars() {
    return new TreeGrid().ground(-5, -5, 5, 8, 62, Id.minecraft("dirt")).ground(-5, -5, 5, 8, 63, Id.minecraft("grass_block"))
        .column(0, 0, 64, 71, Id.minecraft("birch_log")).column(0, 3, 64, 69, Id.minecraft("birch_log"))
        .set(0, 63, 1, Id.minecraft("birch_log")).set(0, 63, 2, Id.minecraft("birch_log"))
        .canopy(0, 68, 72, 0, 1, Id.minecraft("birch_leaves")).canopy(0, 67, 70, 3, 1, Id.minecraft("birch_leaves"));
  }

  static TreeGrid mangrove() {
    return new TreeGrid().ground(-4, -4, 4, 4, 62, Id.minecraft("mud")).set(0, 62, 0, Id.minecraft("muddy_mangrove_roots"))
        .set(0, 63, 0, Id.minecraft("mangrove_roots")).set(1, 63, 0, Id.minecraft("mangrove_roots")).set(-1, 63, 0, Id.minecraft("mangrove_roots"))
        .set(0, 63, 1, Id.minecraft("mangrove_roots")).set(2, 63, 0, Id.minecraft("water")).set(1, 64, 0, Id.minecraft("mangrove_roots"))
        .set(-1, 64, 0, Id.minecraft("moss_carpet")).column(0, 0, 64, 68, Id.minecraft("mangrove_log"))
        .canopy(0, 66, 69, 0, 2, Id.minecraft("mangrove_leaves")).set(2, 65, 2, Id.minecraft("mangrove_propagule"));
  }

  static TreeGrid crimson() {
    return new TreeGrid().ground(-5, -5, 5, 5, 62, Id.minecraft("netherrack")).ground(-5, -5, 5, 5, 63, Id.minecraft("crimson_nylium"))
        .column(0, 0, 64, 69, Id.minecraft("crimson_stem")).set(1, 64, 0, Id.minecraft("crimson_roots"))
        .canopy(0, 67, 70, 0, 2, Id.minecraft("nether_wart_block")).set(1, 68, 1, Id.minecraft("shroomlight"))
        .set(2, 66, 2, Id.minecraft("weeping_vines")).set(3, 68, 0, Id.minecraft("nether_wart_block")).set(4, 68, 0, Id.minecraft("nether_wart_block"))
        .stamp(2, 69, 2);
  }

  private static TreeGrid megaSpruce() {
    TreeGrid spruce = ground(Id.minecraft("podzol"));
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        spruce.column(x, z, 64, 71, Id.minecraft("spruce_log"));
      }
    }
    return spruce.canopy(0, 70, 72, 0, 3, Id.minecraft("spruce_leaves"));
  }

  private static TreeGrid ground(Id surface) {
    return new TreeGrid().ground(-5, -5, 5, 5, 63, surface);
  }

  private static String verdictAndLogs(TreeGrid grid, LumberTrees.Reach reach) {
    return verdictAndLogs(grid, 0, reach);
  }

  private static String verdictAndLogs(TreeGrid grid, int x, LumberTrees.Reach reach) {
    LumberTrees.Survey survey = LumberTrees.survey(grid, x, 64, 0, reach);
    return survey.verdict() + " " + (survey.tree() == null ? 0 : survey.tree().logs());
  }

  private static String position(LumberTrees.Part part) {
    return part.x() + "," + part.y() + "," + part.z();
  }

  private static Id known(String block) {
    AdaptCore core = AdaptCore.current();
    Id id = Id.minecraft(block);
    return core != null && core.getPlatform().host().registries().exists(RegistryKind.BLOCK, id) ? id : null;
  }
}
