package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LumberTreesTest {
  private static final LumberTrees.Reach SMALL = new LumberTrees.Reach(false, false, 64);
  private static final LumberTrees.Reach MEGA = new LumberTrees.Reach(true, false, 256);
  private static final LumberTrees.Reach NETHER = new LumberTrees.Reach(true, true, 256);

  @Test
  void anOakOnDirtIsANaturalTreeFelledFromTheTopDown() {
    LumberTrees.Survey survey = LumberTrees.survey(oak(), 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    LumberTrees.Tree tree = survey.tree();
    assertThat(tree.species()).isEqualTo(Material.OAK_LOG);
    assertThat(tree.logs()).isEqualTo(5);
    assertThat(position(tree.base())).isEqualTo("0,64,0");
    assertThat(tree.bases()).hasSize(1);
    assertThat(tree.parts()).extracting(LumberTrees.Part::y).containsExactly(68, 67, 66, 65, 64);
    assertThat(tree.parts()).extracting(LumberTrees.Part::type).containsOnly(Material.OAK_LOG);
    assertThat(tree.nether()).isFalse();
  }

  @Test
  void birchSpruceAndCherryTreesAreNatural() {
    TreeGrid birch = ground(Material.GRASS_BLOCK).column(0, 0, 64, 69, Material.BIRCH_LOG)
        .canopy(0, 67, 68, 0, 2, Material.BIRCH_LEAVES).canopy(0, 69, 70, 0, 1, Material.BIRCH_LEAVES);
    TreeGrid spruce = ground(Material.PODZOL).column(0, 0, 64, 71, Material.SPRUCE_LOG)
        .canopy(0, 66, 69, 0, 2, Material.SPRUCE_LEAVES).canopy(0, 70, 72, 0, 1, Material.SPRUCE_LEAVES).set(1, 64, 0, Material.FERN);
    TreeGrid cherry = ground(Material.GRASS_BLOCK).column(0, 0, 64, 67, Material.CHERRY_LOG)
        .set(1, 67, 0, Material.CHERRY_LOG).set(2, 67, 0, Material.CHERRY_LOG).set(2, 68, 0, Material.CHERRY_LOG)
        .canopy(2, 69, 70, 0, 2, Material.CHERRY_LEAVES).set(-1, 64, 0, Material.PINK_PETALS);

    assertThat(verdictAndLogs(birch, SMALL)).isEqualTo("NATURAL 6");
    assertThat(verdictAndLogs(spruce, SMALL)).isEqualTo("NATURAL 8");
    assertThat(verdictAndLogs(cherry, SMALL)).isEqualTo("NATURAL 7");
  }

  @Test
  void acaciaBranchesThatOnlyTouchAtCornersStayInTheTree() {
    TreeGrid acacia = ground(Material.GRASS_BLOCK).column(0, 0, 64, 65, Material.ACACIA_LOG)
        .set(1, 66, 1, Material.ACACIA_LOG).set(2, 67, 2, Material.ACACIA_LOG).set(-1, 66, -1, Material.ACACIA_LOG)
        .canopy(2, 68, 68, 2, 2, Material.ACACIA_LEAVES).canopy(-1, 67, 67, -1, 1, Material.ACACIA_LEAVES);

    LumberTrees.Survey survey = LumberTrees.survey(acacia, 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().parts()).extracting(LumberTreesTest::position).containsExactly("2,67,2", "-1,66,-1", "1,66,1", "0,65,0", "0,64,0");
  }

  @Test
  void jungleTrunksWithCocoaVinesAndBeeNestsAreNatural() {
    TreeGrid jungle = ground(Material.GRASS_BLOCK).column(0, 0, 64, 73, Material.JUNGLE_LOG)
        .set(1, 66, 0, Material.COCOA).set(0, 67, -1, Material.VINE).set(-1, 70, 0, Material.BEE_NEST)
        .canopy(0, 72, 74, 0, 2, Material.JUNGLE_LEAVES);

    assertThat(verdictAndLogs(jungle, SMALL)).isEqualTo("NATURAL 10");
  }

  @Test
  void megaTreesNeedTheMegaLevelAndAreFelledAsOneTree() {
    TreeGrid spruce = ground(Material.PODZOL);
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        spruce.column(x, z, 64, 75, Material.SPRUCE_LOG);
      }
    }
    spruce.canopy(0, 70, 76, 0, 3, Material.SPRUCE_LEAVES);

    assertThat(LumberTrees.survey(spruce, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    assertThat(spruce.reads()).isLessThan(100);
    LumberTrees.Survey survey = LumberTrees.survey(spruce, 0, 64, 0, MEGA);
    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().logs()).isEqualTo(48);
    assertThat(survey.tree().bases()).extracting(LumberTreesTest::position).containsExactlyInAnyOrder("0,64,0", "1,64,0", "0,64,1", "1,64,1");
    assertThat(position(survey.tree().base())).isEqualTo("0,64,0");
    assertThat(LumberTrees.survey(spruce, 1, 64, 1, MEGA).tree().base()).isEqualTo(survey.tree().base());
  }

  @Test
  void aDarkOakWithABranchIsAMegaTree() {
    TreeGrid darkOak = ground(Material.GRASS_BLOCK);
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        darkOak.column(x, z, 64, 69, Material.DARK_OAK_LOG);
      }
    }
    darkOak.set(2, 70, 2, Material.DARK_OAK_LOG).canopy(1, 69, 71, 1, 3, Material.DARK_OAK_LEAVES).set(-1, 64, 0, Material.BROWN_MUSHROOM);

    assertThat(LumberTrees.survey(darkOak, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
    assertThat(verdictAndLogs(darkOak, MEGA)).isEqualTo("NATURAL 25");
    assertThat(LumberTrees.survey(darkOak, 0, 64, 0, MEGA).tree().parts().get(0)).isEqualTo(new LumberTrees.Part(2, 70, 2, Material.DARK_OAK_LOG));
  }

  @Test
  void onlyTheNorthWestBaseOfAWideTrunkIsACandidate() {
    TreeGrid spruce = ground(Material.PODZOL);
    for (int x = 0; x <= 1; x++) {
      for (int z = 0; z <= 1; z++) {
        spruce.column(x, z, 64, 70, Material.SPRUCE_LOG);
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
  void mangrovesStandOnTheirOwnRootsAboveTheMud() {
    TreeGrid mangrove = mangrove();

    assertThat(LumberTrees.candidate(mangrove, 0, 64, 0, false)).isTrue();
    LumberTrees.Survey survey = LumberTrees.survey(mangrove, 0, 64, 0, SMALL);
    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().logs()).isEqualTo(5);
    assertThat(survey.tree().parts()).extracting(LumberTrees.Part::type).containsOnly(Material.MANGROVE_LOG);

    mangrove.stamp(0, 63, 0);
    assertThat(LumberTrees.candidate(mangrove, 0, 64, 0, false)).isFalse();
    assertThat(LumberTrees.survey(mangrove, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
  }

  @Test
  void otherLogsNeverStandOnMangroveRoots() {
    TreeGrid oak = oak().set(0, 63, 0, Material.MANGROVE_ROOTS);

    assertThat(LumberTrees.survey(oak, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
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
    assertThat(tree.parts()).contains(new LumberTrees.Part(1, 68, 1, Material.SHROOMLIGHT));
    assertThat(tree.parts().get(0).y()).isEqualTo(70);
    assertThat(tree.parts()).extracting(LumberTrees.Part::y).isSortedAccordingTo((first, second) -> Integer.compare(second, first));
    int stem = parts.indexOf("0,69,0");
    assertThat(tree.parts().subList(stem + 1, tree.parts().size())).noneMatch(part -> part.y() == 69 && part.type() != Material.CRIMSON_STEM);
  }

  @Test
  void logsTouchingBuildingBlocksAreNeverCut() {
    for (Material built : List.of(Material.OAK_PLANKS, Material.GLASS, Material.OAK_STAIRS, Material.TORCH, Material.COBBLESTONE,
        Material.OAK_FENCE, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD, Material.CHEST, Material.WHITE_WOOL)) {
      TreeGrid house = oak().set(1, 65, 0, built);

      assertThat(LumberTrees.survey(house, 0, 64, 0, SMALL).verdict()).as("log touching %s", built).isEqualTo(LumberTrees.Verdict.BUILT);
    }
  }

  @Test
  void placedLeavesTouchingALogMarkABuild() {
    TreeGrid roof = oak().persist(1, 66, 0);

    assertThat(LumberTrees.survey(roof, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.BUILT);
  }

  @Test
  void aLogCabinOfPillarsAndPlanksIsNeverATree() {
    TreeGrid cabin = ground(Material.GRASS_BLOCK);
    for (int x = 0; x <= 3; x++) {
      for (int z = 0; z <= 3; z++) {
        boolean corner = (x == 0 || x == 3) && (z == 0 || z == 3);
        boolean wall = x == 0 || x == 3 || z == 0 || z == 3;
        if (wall) {
          cabin.column(x, z, 64, 66, corner ? Material.SPRUCE_LOG : Material.SPRUCE_PLANKS);
        }
        cabin.set(x, 67, z, Material.SPRUCE_LEAVES).persist(x, 67, z);
      }
    }

    assertThat(LumberTrees.survey(cabin, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.BUILT);
  }

  @Test
  void aPlacedStampOnAnUpperLogRejectsTheTreeButTheSaplingSpotDoesNot() {
    assertThat(LumberTrees.survey(oak().stamp(0, 66, 0), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.survey(oak().stamp(0, 64, 0), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void aTreeNeedsNaturalLeavesWithinTwoBlocksOfItsTopLogs() {
    TreeGrid pillar = ground(Material.GRASS_BLOCK).column(0, 0, 64, 68, Material.OAK_LOG);
    TreeGrid distant = ground(Material.GRASS_BLOCK).column(0, 0, 64, 68, Material.OAK_LOG).canopy(3, 72, 72, 0, 0, Material.OAK_LEAVES);
    TreeGrid decorated = ground(Material.GRASS_BLOCK).column(0, 0, 64, 68, Material.OAK_LOG)
        .set(2, 69, 0, Material.OAK_LEAVES).persist(2, 69, 0);
    TreeGrid near = ground(Material.GRASS_BLOCK).column(0, 0, 64, 68, Material.OAK_LOG).set(2, 70, 2, Material.OAK_LEAVES);

    assertThat(LumberTrees.survey(pillar, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NO_LEAVES);
    assertThat(LumberTrees.survey(distant, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NO_LEAVES);
    assertThat(LumberTrees.survey(decorated, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NO_LEAVES);
    assertThat(LumberTrees.survey(near, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void treesWithMoreLogsThanTheCapAreLeftStanding() {
    assertThat(LumberTrees.survey(oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 4)).verdict()).isEqualTo(LumberTrees.Verdict.TOO_BIG);
    assertThat(LumberTrees.survey(oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 5)).verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void aTreeReachingIntoAnotherRegionIsLeftStanding() {
    assertThat(LumberTrees.survey(oak().foreign(1, 66, 0), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.UNOWNED);
    assertThat(LumberTrees.survey(oak().foreign(1, 67, 1), 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.UNOWNED);
    assertThat(LumberTrees.candidate(oak().foreign(0, 63, 0), 0, 64, 0, false)).isFalse();
  }

  @Test
  void aLoneLogALogOffTheGroundOrANonLogIsNoTree() {
    TreeGrid lone = ground(Material.GRASS_BLOCK).set(0, 64, 0, Material.OAK_LOG).canopy(0, 65, 65, 0, 1, Material.OAK_LEAVES);
    TreeGrid stone = oak().set(0, 63, 0, Material.STONE);

    assertThat(LumberTrees.survey(lone, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
    assertThat(LumberTrees.survey(stone, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
    assertThat(LumberTrees.survey(oak(), 0, 65, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
    assertThat(LumberTrees.survey(oak(), 0, 63, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.NOT_A_TREE);
  }

  @Test
  void twoTreesJoinedByABranchAreTooWideEvenAtTheMegaLevel() {
    TreeGrid joined = oak().column(4, 0, 64, 68, Material.OAK_LOG).canopy(4, 66, 67, 0, 1, Material.OAK_LEAVES)
        .set(1, 68, 0, Material.OAK_LOG).set(2, 68, 0, Material.OAK_LOG).set(3, 68, 0, Material.OAK_LOG);

    assertThat(LumberTrees.survey(joined, 0, 64, 0, MEGA).verdict()).isEqualTo(LumberTrees.Verdict.TOO_WIDE);
  }

  @Test
  void anotherSpeciesTouchingTheTrunkIsLeftOutOfTheTree() {
    TreeGrid mixed = oak().column(1, 0, 64, 66, Material.BIRCH_LOG);

    LumberTrees.Survey survey = LumberTrees.survey(mixed, 0, 64, 0, SMALL);

    assertThat(survey.verdict()).isEqualTo(LumberTrees.Verdict.NATURAL);
    assertThat(survey.tree().parts()).extracting(LumberTrees.Part::type).containsOnly(Material.OAK_LOG);
  }

  @Test
  void aLogStaysIntactUntilSomethingIsBuiltAgainstIt() {
    TreeGrid tree = oak();
    LumberTrees.Part log = new LumberTrees.Part(0, 66, 0, Material.OAK_LOG);

    assertThat(LumberTrees.intact(tree, log)).isEqualTo(LumberTrees.Verdict.NATURAL);
    tree.set(0, 66, 1, Material.OAK_PLANKS);
    assertThat(LumberTrees.intact(tree, log)).isEqualTo(LumberTrees.Verdict.BUILT);
    assertThat(LumberTrees.intact(oak().stamp(0, 66, 0), log)).isEqualTo(LumberTrees.Verdict.PLACED);
    assertThat(LumberTrees.intact(oak().stamp(0, 64, 0), new LumberTrees.Part(0, 64, 0, Material.OAK_LOG))).isEqualTo(LumberTrees.Verdict.NATURAL);
  }

  @Test
  void leafSweepCandidatesAreTheLeavesWithinSixStepsOfTheFelledLogs() {
    TreeGrid tree = oak();
    for (int x = 3; x <= 9; x++) {
      tree.set(x, 66, 0, Material.OAK_LEAVES);
    }
    List<LumberTrees.Part> logs = LumberTrees.survey(tree, 0, 64, 0, SMALL).tree().parts();
    for (LumberTrees.Part log : logs) {
      tree.set(log.x(), log.y(), log.z(), Material.AIR);
    }
    tree.loosen(1, 66, 0).loosen(1, 67, 0).persist(1, 67, 0);

    List<String> candidates = new ArrayList<>();
    for (LumberTrees.Part leaf : LumberTrees.leafCandidates(tree, logs)) {
      candidates.add(position(leaf));
    }

    assertThat(candidates).hasSize(68).contains("6,66,0").doesNotContain("7,66,0", "1,67,0");
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(1, 66, 0, Material.OAK_LEAVES))).isTrue();
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(2, 66, 0, Material.OAK_LEAVES))).isFalse();
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(1, 67, 0, Material.OAK_LEAVES))).isFalse();
    tree.set(1, 66, 0, Material.AIR);
    assertThat(LumberTrees.sweepable(tree, new LumberTrees.Part(1, 66, 0, Material.OAK_LEAVES))).isFalse();
  }

  @Test
  void aHouseIsRejectedBeforeTheWholeFrameIsRead() {
    TreeGrid house = oak().set(0, 64, 1, Material.OAK_PLANKS);

    assertThat(LumberTrees.survey(house, 0, 64, 0, SMALL).verdict()).isEqualTo(LumberTrees.Verdict.BUILT);
    assertThat(house.reads()).isLessThan(40);
  }

  static TreeGrid oak() {
    return ground(Material.GRASS_BLOCK).set(0, 63, 0, Material.DIRT).column(0, 0, 64, 68, Material.OAK_LOG)
        .canopy(0, 66, 67, 0, 2, Material.OAK_LEAVES).canopy(0, 68, 69, 0, 1, Material.OAK_LEAVES);
  }

  static TreeGrid mangrove() {
    return new TreeGrid().ground(-4, -4, 4, 4, 62, Material.MUD).set(0, 62, 0, Material.MUDDY_MANGROVE_ROOTS)
        .set(0, 63, 0, Material.MANGROVE_ROOTS).set(1, 63, 0, Material.MANGROVE_ROOTS).set(-1, 63, 0, Material.MANGROVE_ROOTS)
        .set(0, 63, 1, Material.MANGROVE_ROOTS).set(2, 63, 0, Material.WATER).set(1, 64, 0, Material.MANGROVE_ROOTS)
        .set(-1, 64, 0, Material.MOSS_CARPET).column(0, 0, 64, 68, Material.MANGROVE_LOG)
        .canopy(0, 66, 69, 0, 2, Material.MANGROVE_LEAVES).set(2, 65, 2, Material.MANGROVE_PROPAGULE);
  }

  static TreeGrid crimson() {
    return new TreeGrid().ground(-5, -5, 5, 5, 62, Material.NETHERRACK).ground(-5, -5, 5, 5, 63, Material.CRIMSON_NYLIUM)
        .column(0, 0, 64, 69, Material.CRIMSON_STEM).set(1, 64, 0, Material.CRIMSON_ROOTS)
        .canopy(0, 67, 70, 0, 2, Material.NETHER_WART_BLOCK).set(1, 68, 1, Material.SHROOMLIGHT)
        .set(2, 66, 2, Material.WEEPING_VINES).set(3, 68, 0, Material.NETHER_WART_BLOCK).set(4, 68, 0, Material.NETHER_WART_BLOCK)
        .stamp(2, 69, 2);
  }

  private static TreeGrid ground(Material surface) {
    return new TreeGrid().ground(-5, -5, 5, 5, 63, surface);
  }

  private static String verdictAndLogs(TreeGrid grid, LumberTrees.Reach reach) {
    LumberTrees.Survey survey = LumberTrees.survey(grid, 0, 64, 0, reach);
    return survey.verdict() + " " + (survey.tree() == null ? 0 : survey.tree().logs());
  }

  private static String position(LumberTrees.Part part) {
    return part.x() + "," + part.y() + "," + part.z();
  }
}
