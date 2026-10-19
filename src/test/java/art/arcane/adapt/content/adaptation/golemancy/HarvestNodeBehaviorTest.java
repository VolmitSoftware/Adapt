package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import art.arcane.adapt.api.golem.GolemTaskBoards;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.xp.PlacementStore;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.api.xp.XpProvenanceListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarvestNodeBehaviorTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000022c1");
  private static final UUID OTHER_GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000022c2");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);
  private static final long NOW = 50_000L;

  private final GolemHarvesterCore.Config config = new GolemHarvesterCore.Config();
  private final CropField field = new CropField();
  private final HarvestSpots spots = new HarvestSpots();
  private final List<GolemTask> posted = new ArrayList<>();
  private final List<ItemStack> carried = new ArrayList<>();
  private final List<Double> xp = new ArrayList<>();
  private final NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(2, 1, 2).build();
  private final GolemTaskBoards boards = new GolemTaskBoards(key -> Optional.of(record));
  private GolemHarvesterCore core;
  private HarvestActions actions;
  private HarvestNodeBehavior behavior;
  private double roll = 0.99D;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<HarvestFeedback> feedback;
  private MockedStatic<XpProvenance> provenance;

  @BeforeEach
  void setUp() {
    core = mock(GolemHarvesterCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.crops()).thenReturn(HarvestCrops.parse(config.crops));
    lenient().when(core.tends(OWNER)).thenReturn(true);
    lenient().when(core.tills(OWNER)).thenReturn(true);
    level(5);
    actions = new HarvestActions(core, () -> fixed(roll));
    behavior = new HarvestNodeBehavior(core, spots, actions);
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    feedback = mockStatic(HarvestFeedback.class);
    provenance = mockStatic(XpProvenance.class);
    provenance.when(() -> XpProvenance.forgetPlacement(any(Block.class))).thenReturn(false);
    runtime.when(GolemancyRuntime::boards).thenReturn(Optional.of(boards));
  }

  @AfterEach
  void close() {
    provenance.close();
    feedback.close();
    runtime.close();
  }

  @Test
  void itActsForTheHarvesterCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(core);
  }

  @Test
  void aScanPostsHarvestAndTillJobsAndRemembersYoungCropsAndEmptyFarmland() {
    level(4);
    plot();

    behavior.scan(node(), budget(0, 75, true));

    assertThat(jobs()).containsExactly("harvest -1,64,-1", "till -1,63,1");
    assertThat(spotsOf()).containsExactly("PLANT 1,63,1", "TEND 0,64,-1");
  }

  @Test
  void anOwnerWhoSwitchesTillingOffGetsNoTillJobsOrEmptyFarmlandAndTillingFails() {
    when(core.tills(OWNER)).thenReturn(false);
    level(4);
    plot();

    behavior.scan(node(), budget(0, 75, true));

    assertThat(jobs()).containsExactly("harvest -1,64,-1");
    assertThat(spotsOf()).containsExactly("TEND 0,64,-1");
    assertThat(behavior.act(golem(1), task("till", -1, 63, 1))).isEqualTo(GolemActResult.FAILED);
    assertThat(field.type(-1, 63, 1)).isEqualTo(Material.DIRT);
  }

  @Test
  void anOwnerWhoSwitchesTendingOffLeavesYoungCropsUnmarked() {
    when(core.tends(OWNER)).thenReturn(false);
    level(4);
    plot();

    behavior.scan(node(), budget(0, 75, true));

    assertThat(jobs()).containsExactly("harvest -1,64,-1", "till -1,63,1");
    assertThat(spotsOf()).containsExactly("PLANT 1,63,1");
  }

  @Test
  void levelOneScansPostOnlyHarvestJobs() {
    level(1);
    plot();

    behavior.scan(node(), budget(0, 75, true));

    assertThat(jobs()).containsExactly("harvest -1,64,-1");
    assertThat(spotsOf()).isEmpty();
  }

  @Test
  void anOwnerWithoutTheCorePostsNothing() {
    level(0);
    plot();

    behavior.scan(node(), budget(0, 75, true));

    assertThat(posted).isEmpty();
    assertThat(spotsOf()).isEmpty();
  }

  @Test
  void aJobWhoseTargetChangedIsWithdrawnAndAnOpenJobIsNeverPostedTwice() {
    plot();
    field.set(0, 64, 0, Material.CARROTS, CropField.ageable(0, 7));
    GolemTask stale = task("harvest", 0, 64, 0);
    GolemTask open = task("harvest", -1, 64, -1);
    posted.add(stale);
    posted.add(open);

    behavior.scan(node(), budget(0, 75, true));

    assertThat(posted).contains(open).doesNotContain(stale);
    assertThat(jobs()).containsOnlyOnce("harvest -1,64,-1");
  }

  @Test
  void aFullNodePostsNothingButStillRemembersSpots() {
    level(4);
    plot();

    behavior.scan(node(), budget(0, 75, false));

    assertThat(posted).isEmpty();
    assertThat(spotsOf()).containsExactly("PLANT 1,63,1", "TEND 0,64,-1");
  }

  @Test
  void theFirstActWindsUpAndTheNextHarvestsAndReplantsFromTheDrops() {
    wheat(0, 64, 0);
    GolemContext golem = golem(0);
    GolemTask task = task("harvest", 0, 64, 0);

    assertThat(behavior.act(golem, task)).isEqualTo(GolemActResult.WORKING);
    assertThat(field.age(0, 64, 0)).isEqualTo(7);

    golem = golem(1);
    assertThat(behavior.act(golem, task)).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.WHEAT);
    assertThat(field.age(0, 64, 0)).isZero();
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("WHEAT", 1, "WHEAT_SEEDS", 1));
    assertThat(xp).containsExactly(0.4D);
    feedback.verify(() -> HarvestFeedback.play(eq(core), any(Location.class), eq(HarvestFeedback.Kind.HARVEST)));
  }

  @Test
  void cropsWithoutASeedInTheirDropsAreLeftEmpty() {
    field.set(0, 64, 0, Material.PITCHER_CROP, CropField.pitcher(4, Bisected.Half.BOTTOM));
    field.set(0, 65, 0, Material.PITCHER_CROP, CropField.pitcher(4, Bisected.Half.TOP));
    field.drops(Material.PITCHER_CROP, () -> CropField.stacks(Material.PITCHER_PLANT, 1));
    field.set(1, 63, 0, Material.FARMLAND).set(1, 64, 0, Material.TORCHFLOWER);
    field.drops(Material.TORCHFLOWER, () -> CropField.stacks(Material.TORCHFLOWER, 1));

    assertThat(behavior.act(golem(1), task("harvest", 0, 64, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(1), task("harvest", 1, 64, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AIR);
    assertThat(field.type(0, 65, 0)).isEqualTo(Material.AIR);
    assertThat(field.physicsFree).contains("0,65,0");
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.AIR);
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("PITCHER_PLANT", 1, "TORCHFLOWER", 1));
  }

  @Test
  void aTorchflowerWithSeedsInItsDropsGoesBackToACrop() {
    field.set(1, 63, 0, Material.FARMLAND).set(1, 64, 0, Material.TORCHFLOWER);
    field.drops(Material.TORCHFLOWER, () -> CropField.stacks(Material.TORCHFLOWER, 1, Material.TORCHFLOWER_SEEDS, 1));

    assertThat(behavior.act(golem(1), task("harvest", 1, 64, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(1, 64, 0)).isEqualTo(Material.TORCHFLOWER_CROP);
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("TORCHFLOWER", 1));
  }

  @Test
  void berriesArePickedWithoutBreakingThePlant() {
    field.set(0, 64, 0, Material.SWEET_BERRY_BUSH, CropField.ageable(3, 3));
    field.drops(Material.SWEET_BERRY_BUSH, () -> CropField.stacks(Material.SWEET_BERRIES, 3));
    field.set(1, 64, 0, Material.CAVE_VINES_PLANT, CropField.vines(true));
    field.drops(Material.CAVE_VINES_PLANT, () -> CropField.stacks(Material.GLOW_BERRIES, 1));

    assertThat(behavior.act(golem(1), task("harvest", 0, 64, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(1), task("harvest", 1, 64, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.SWEET_BERRY_BUSH);
    assertThat(field.age(0, 64, 0)).isEqualTo(1);
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.CAVE_VINES_PLANT);
    assertThat(((CaveVinesPlant) field.data(1, 64, 0)).hasBerries()).isFalse();
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("SWEET_BERRIES", 3, "GLOW_BERRIES", 1));
    feedback.verify(() -> HarvestFeedback.play(eq(core), any(Location.class), eq(HarvestFeedback.Kind.HARVEST)), never());
  }

  @Test
  void melonsLeaveTheirStemAndColumnsLeaveTheirBase() {
    field.set(0, 64, 0, Material.MELON).set(1, 64, 0, Material.ATTACHED_MELON_STEM, CropField.facing(BlockFace.WEST));
    field.drops(Material.MELON, () -> CropField.stacks(Material.MELON_SLICE, 5));
    for (int y = 64; y <= 66; y++) {
      field.set(-1, y, 0, Material.SUGAR_CANE);
    }
    field.drops(Material.SUGAR_CANE, () -> CropField.stacks(Material.SUGAR_CANE, 1));

    assertThat(behavior.act(golem(1), task("harvest", 0, 64, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(1), task("harvest", -1, 65, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AIR);
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.ATTACHED_MELON_STEM);
    assertThat(field.type(-1, 64, 0)).isEqualTo(Material.SUGAR_CANE);
    assertThat(field.type(-1, 65, 0)).isEqualTo(Material.AIR);
    assertThat(field.type(-1, 66, 0)).isEqualTo(Material.AIR);
    assertThat(field.physicsFree).containsSubsequence("-1,66,0", "-1,65,0");
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("MELON_SLICE", 5, "SUGAR_CANE", 2));
    assertThat(xp).containsExactly(0.4D, 0.4D);
  }

  @Test
  void aTargetThatIsNoLongerRipeFailsAndProtectionBlocksTheJob() {
    field.set(0, 64, 0, Material.WHEAT, CropField.ageable(3, 7));
    wheat(1, 64, 0);
    GolemContext denied = golem(1);
    when(denied.canBreak(any(Location.class))).thenReturn(false);

    assertThat(behavior.act(golem(1), task("harvest", 0, 64, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(denied, task("harvest", 1, 64, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(field.age(1, 64, 0)).isEqualTo(7);
  }

  @Test
  void aGolemWhoseOwnerLostTheCoreDoesNothing() {
    level(0);
    wheat(0, 64, 0);

    assertThat(behavior.act(golem(1), task("harvest", 0, 64, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(field.age(0, 64, 0)).isEqualTo(7);
  }

  @Test
  void bountifulRollsTheDropsAgainAtLevelFiveAndEntropyAppliesAfterIt() {
    wheat(0, 64, 0);
    wheat(1, 64, 0);
    roll = 0.05D;
    GolemContext golem = golem(1);
    when(golem.withBonusYield(anyList())).thenAnswer(invocation -> {
      List<ItemStack> drops = new ArrayList<>(invocation.getArgument(0));
      drops.add(GolemTestItems.stack(Material.WHEAT, 1));
      return drops;
    });

    behavior.act(golem, task("harvest", 0, 64, 0));
    level(4);
    behavior.act(golem, task("harvest", 1, 64, 0));

    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("WHEAT", 5, "WHEAT_SEEDS", 4));
  }

  @Test
  void handPlantedCropsKeepTheirBountifulRollAndBonusYield() {
    wheat(0, 64, 0);
    roll = 0.05D;
    provenance.when(() -> XpProvenance.isPlayerPlaced(any(Block.class))).thenReturn(true);
    provenance.when(() -> XpProvenance.forgetPlacement(any(Block.class))).thenReturn(true);
    GolemContext golem = golem(1);

    behavior.act(golem, task("harvest", 0, 64, 0));

    verify(golem).withBonusYield(anyList());
    assertThat(carriedCounts()).containsEntry("WHEAT", 2);
  }

  @Test
  void placedGourdsAndColumnPartsGetNoBountifulRollOrBonusYieldEvenAfterThePlacementTtl() {
    field.set(0, 64, 0, Material.MELON).set(1, 64, 0, Material.ATTACHED_MELON_STEM, CropField.facing(BlockFace.WEST));
    field.drops(Material.MELON, () -> CropField.stacks(Material.MELON_SLICE, 5));
    for (int y = 64; y <= 66; y++) {
      field.set(-1, y, 0, Material.SUGAR_CANE);
    }
    field.drops(Material.SUGAR_CANE, () -> CropField.stacks(Material.SUGAR_CANE, 1));
    roll = 0.05D;
    provenance.when(() -> XpProvenance.isPlayerPlaced(any(Block.class))).thenReturn(false);
    provenance.when(() -> XpProvenance.forgetPlacement(any(Block.class))).thenReturn(true);
    GolemContext golem = golem(1);

    behavior.act(golem, task("harvest", 0, 64, 0));
    behavior.act(golem, task("harvest", -1, 65, 0));

    verify(golem, never()).withBonusYield(anyList());
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("MELON_SLICE", 5, "SUGAR_CANE", 2));
  }

  @Test
  void aPlacedTorchflowerGetsNoBountifulRollOrBonusYieldAndTheHarvestForgetsThePlacement() {
    realProvenance();
    try (PlacementStore placements = new PlacementStore()) {
      field.set(1, 63, 0, Material.FARMLAND).set(1, 64, 0, Material.TORCHFLOWER);
      field.drops(Material.TORCHFLOWER, () -> CropField.stacks(Material.TORCHFLOWER, 1));
      XpProvenance.recordPlacement(field.block(1, 64, 0));
      roll = 0.05D;
      GolemContext golem = golem(1);

      behavior.act(golem, task("harvest", 1, 64, 0));

      verify(golem, never()).withBonusYield(anyList());
      assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("TORCHFLOWER", 1));
      assertThat(XpProvenance.wasPlaced(field.block(1, 64, 0))).isFalse();
    }
  }

  @Test
  void aTorchflowerGrownFromPlantedSeedsGetsItsBountifulRollAndBonusYield() {
    realProvenance();
    try (PlacementStore placements = new PlacementStore()) {
      field.set(1, 63, 0, Material.FARMLAND).set(1, 64, 0, Material.TORCHFLOWER_CROP, CropField.ageable(1, 2));
      field.drops(Material.TORCHFLOWER, () -> CropField.stacks(Material.TORCHFLOWER, 1));
      XpProvenance.recordPlacement(field.block(1, 64, 0));
      grow(field.block(1, 64, 0), Material.TORCHFLOWER);
      roll = 0.05D;
      GolemContext golem = golem(1);

      behavior.act(golem, task("harvest", 1, 64, 0));

      verify(golem).withBonusYield(anyList());
      assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("TORCHFLOWER", 2));
    }
  }

  @Test
  void aPumpkinRegrownOnAStalePlacementSpotGetsItsBountifulRollAndBonusYield() {
    realProvenance();
    try (PlacementStore placements = new PlacementStore()) {
      field.set(1, 64, 0, Material.ATTACHED_PUMPKIN_STEM, CropField.facing(BlockFace.WEST));
      field.drops(Material.PUMPKIN, () -> CropField.stacks(Material.PUMPKIN, 1));
      placements.put(field.block(0, 64, 0), new PlacementStamp(1, 0, 0));
      grow(field.block(0, 64, 0), Material.PUMPKIN);
      roll = 0.05D;
      GolemContext golem = golem(1);

      behavior.act(golem, task("harvest", 0, 64, 0));

      verify(golem).withBonusYield(anyList());
      assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("PUMPKIN", 2));
    }
  }

  @Test
  void higherLevelsHarvestNearbyOpenJobsInTheSameAction() {
    level(3);
    GolemTaskBoard board = boards.board(CropField.WORLD);
    for (int x = 0; x <= 2; x++) {
      wheat(x, 64, 0);
    }
    wheat(9, 64, 0);
    GolemTask target = offer(board, 0, 64, 0);
    GolemTask held = offer(board, 1, 64, 0);
    GolemTask near = offer(board, 2, 64, 0);
    GolemTask far = offer(board, 9, 64, 0);
    claimFor(board, OTHER_GOLEM, held);

    assertThat(behavior.act(golem(1), target)).isEqualTo(GolemActResult.DONE);

    assertThat(field.age(0, 64, 0)).isZero();
    assertThat(field.age(2, 64, 0)).isZero();
    assertThat(field.age(1, 64, 0)).isEqualTo(7);
    assertThat(field.age(9, 64, 0)).isEqualTo(7);
    assertThat(board.tasks(KEY)).contains(target, held, far).doesNotContain(near);
    assertThat(xp).containsExactly(0.8D);
  }

  @Test
  void tillingTurnsBareDirtIntoFarmlandAndPlantsACarriedSeed() {
    level(4);
    field.set(0, 63, 0, Material.DIRT);
    field.set(1, 63, 0, Material.DIRT);
    GolemContext seeded = golem(1);
    List<ItemStack> seeds = List.of(GolemTestItems.stack(Material.WHEAT_SEEDS, 2));
    when(seeded.carriedItems()).thenReturn(seeds);
    when(seeded.take(any(), eq(1))).thenReturn(1);

    assertThat(behavior.act(seeded, task("till", 0, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(1), task("till", 1, 63, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 63, 0)).isEqualTo(Material.FARMLAND);
    assertThat(field.type(0, 64, 0)).isEqualTo(Material.WHEAT);
    assertThat(field.type(1, 63, 0)).isEqualTo(Material.FARMLAND);
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.AIR);
    assertThat(xp).isEmpty();
  }

  @Test
  void tillingNeedsLevelFourAndPermissionToBuild() {
    field.set(0, 63, 0, Material.DIRT);
    GolemContext denied = golem(1);
    when(denied.canPlace(any(Location.class))).thenReturn(false);
    level(3);

    assertThat(behavior.act(golem(1), task("till", 0, 63, 0))).isEqualTo(GolemActResult.FAILED);
    level(4);
    assertThat(behavior.act(denied, task("till", 0, 63, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(field.type(0, 63, 0)).isEqualTo(Material.DIRT);
  }

  @Test
  void tillingLeavesDirtBesideAStemAlone() {
    field.set(0, 63, 0, Material.DIRT).set(1, 64, 0, Material.MELON_STEM, CropField.ageable(7, 7));

    assertThat(behavior.act(golem(1), task("till", 0, 63, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(field.type(0, 63, 0)).isEqualTo(Material.DIRT);
  }

  @Test
  void plantingNeverFillsAFruitSpotBesideAStem() {
    field.set(0, 63, 0, Material.FARMLAND).set(1, 64, 0, Material.PUMPKIN_STEM, CropField.ageable(7, 7));
    GolemContext seeded = golem(1);
    List<ItemStack> seeds = List.of(GolemTestItems.stack(Material.WHEAT_SEEDS, 2));
    when(seeded.carriedItems()).thenReturn(seeds);
    when(seeded.take(any(), eq(1))).thenReturn(1);

    assertThat(actions.plant(seeded, field.block(0, 63, 0), actions.plan(5, record))).isFalse();

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AIR);
    verify(seeded, never()).take(any(), anyInt());
  }

  @Test
  void aDeniedStalkBlockOrPitcherTopBlocksTheWholeHarvest() {
    for (int y = 64; y <= 66; y++) {
      field.set(-1, y, 0, Material.SUGAR_CANE);
    }
    field.drops(Material.SUGAR_CANE, () -> CropField.stacks(Material.SUGAR_CANE, 1));
    field.set(0, 64, 0, Material.PITCHER_CROP, CropField.pitcher(4, Bisected.Half.BOTTOM));
    field.set(0, 65, 0, Material.PITCHER_CROP, CropField.pitcher(4, Bisected.Half.TOP));
    field.drops(Material.PITCHER_CROP, () -> CropField.stacks(Material.PITCHER_PLANT, 1));
    GolemContext golem = golem(1);
    when(golem.canBreak(any(Location.class))).thenAnswer(invocation -> {
      Location at = invocation.getArgument(0);
      return !(at.getBlockX() == -1 && at.getBlockY() == 66) && !(at.getBlockX() == 0 && at.getBlockY() == 65);
    });

    assertThat(behavior.act(golem, task("harvest", -1, 65, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(behavior.act(golem, task("harvest", 0, 64, 0))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(List.of(field.type(-1, 65, 0), field.type(-1, 66, 0), field.type(0, 64, 0), field.type(0, 65, 0)))
        .containsExactly(Material.SUGAR_CANE, Material.SUGAR_CANE, Material.PITCHER_CROP, Material.PITCHER_CROP);
    assertThat(carried).isEmpty();
  }

  @Test
  void jobsOutsideTheNodesCurrentAreaAreWithdrawnAndNeverWorked() {
    plot();
    wheat(3, 64, 3);
    GolemTask outside = task("harvest", 3, 64, 3);
    posted.add(outside);

    behavior.scan(node(), budget(0, 75, true));

    assertThat(posted).doesNotContain(outside);
    assertThat(behavior.act(golem(1), outside)).isEqualTo(GolemActResult.FAILED);
    assertThat(field.age(3, 64, 3)).isEqualTo(7);
  }

  @Test
  void theActionIntervalFollowsTheOwnersLevel() {
    level(1);
    assertThat(behavior.actIntervalTicks(golem(0), task("harvest", 0, 64, 0))).isEqualTo(40);
    level(5);
    assertThat(behavior.actIntervalTicks(golem(0), task("harvest", 0, 64, 0))).isEqualTo(12);
  }

  private void realProvenance() {
    provenance.close();
    provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS);
  }

  private void grow(Block block, Material grown) {
    BlockState state = mock(BlockState.class);
    when(state.getType()).thenReturn(grown);
    new XpProvenanceListener().on(new BlockGrowEvent(block, state));
    field.set(block.getX(), block.getY(), block.getZ(), grown);
  }

  private void plot() {
    field.set(-1, 63, -1, Material.FARMLAND);
    wheat(-1, 64, -1);
    field.set(0, 63, -1, Material.FARMLAND).set(0, 64, -1, Material.CARROTS, CropField.ageable(2, 7));
    field.set(1, 63, 1, Material.FARMLAND);
    field.set(-1, 63, 1, Material.DIRT);
    field.set(0, 63, 0, Material.STONE);
  }

  private void wheat(int x, int y, int z) {
    field.set(x, y, z, Material.WHEAT, CropField.ageable(7, 7));
    field.drops(Material.WHEAT, () -> CropField.stacks(Material.WHEAT, 1, Material.WHEAT_SEEDS, 2));
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(OWNER)).thenReturn(level);
  }

  private NodeContext node() {
    NodeContext node = mock(NodeContext.class);
    lenient().when(node.owner()).thenReturn(OWNER);
    lenient().when(node.key()).thenReturn(KEY);
    lenient().when(node.record()).thenReturn(record);
    lenient().when(node.world()).thenReturn(field.world);
    lenient().when(node.nowMillis()).thenReturn(NOW);
    lenient().when(node.owns(any(Block.class))).thenReturn(true);
    lenient().when(node.block(anyInt())).thenAnswer(invocation -> {
      int[] at = NodeArea.position(KEY, record, invocation.getArgument(0));
      return Optional.of(field.block(at[0], at[1], at[2]));
    });
    lenient().when(node.tasks()).thenAnswer(invocation -> List.copyOf(posted));
    lenient().when(node.task(anyString(), any(GolemTarget.class)))
        .thenAnswer(invocation -> GolemTask.builder(KEY, NodeType.HARVEST, invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(node.post(any(GolemTask.class))).thenAnswer(invocation -> posted.add(invocation.getArgument(0)));
    lenient().when(node.withdraw(any(GolemTask.class))).thenAnswer(invocation -> posted.remove(invocation.<GolemTask>getArgument(0)));
    return node;
  }

  private static ScanBudget budget(int start, int end, boolean room) {
    ScanBudget budget = mock(ScanBudget.class);
    lenient().when(budget.start()).thenReturn(start);
    lenient().when(budget.end()).thenReturn(end);
    lenient().when(budget.hasTaskRoom()).thenReturn(room);
    return budget;
  }

  private GolemContext golem(int actIndex) {
    GolemContext golem = mock(GolemContext.class);
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    Mob body = mock(Mob.class);
    lenient().when(body.getLocation()).thenReturn(new Location(field.world, 0.5D, 64D, 0.5D));
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.golem()).thenReturn(body);
    lenient().when(golem.node()).thenReturn(Optional.of(record));
    lenient().when(golem.world()).thenReturn(field.world);
    lenient().when(golem.nowMillis()).thenReturn(NOW);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.owns(any(Location.class))).thenReturn(true);
    lenient().when(golem.canBreak(any(Location.class))).thenReturn(true);
    lenient().when(golem.canPlace(any(Location.class))).thenReturn(true);
    lenient().when(golem.room()).thenReturn(64);
    lenient().when(golem.carriedItems()).thenReturn(List.of());
    lenient().when(golem.withBonusYield(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(golem.carry(any(ItemStack.class))).thenAnswer(invocation -> {
      carried.add(invocation.getArgument(0));
      return Optional.empty();
    });
    lenient().doAnswer(invocation -> xp.add(invocation.getArgument(0))).when(golem).workXp(anyDouble());
    return golem;
  }

  private static GolemTask task(String type, int x, int y, int z) {
    return GolemTask.builder(KEY, NodeType.HARVEST, type, GolemTarget.block(CropField.WORLD, x, y, z)).build();
  }

  private static GolemTask offer(GolemTaskBoard board, int x, int y, int z) {
    GolemTask task = task("harvest", x, y, z);
    board.offer(task, 16);
    return task;
  }

  private static void claimFor(GolemTaskBoard board, UUID golem, GolemTask wanted) {
    GolemTaskBoard.Seeker seeker = new GolemTaskBoard.Seeker(golem, OWNER, List.of(GolemCoreType.HARVESTER), GolemBody.COPPER, ChannelMask.NONE,
        1D, 64D, 0D, 24D, 1D, 64D, 0D);
    Predicate<GolemTask> others = task -> task != wanted;
    assertThat(board.claim(seeker, others, NOW, 20_000L)).contains(wanted);
  }

  private List<String> jobs() {
    List<String> lines = new ArrayList<>();
    for (GolemTask task : posted) {
      GolemTarget target = task.target();
      lines.add(task.type() + " " + target.blockX() + "," + target.blockY() + "," + target.blockZ());
    }
    lines.sort(null);
    return lines;
  }

  private List<String> spotsOf() {
    List<String> lines = new ArrayList<>();
    for (HarvestSpots.Mark mark : spots.of(KEY)) {
      lines.add(mark.kind() + " " + mark.x() + "," + mark.y() + "," + mark.z());
    }
    lines.sort(null);
    return lines;
  }

  private Map<String, Integer> carriedCounts() {
    Map<String, Integer> counts = new TreeMap<>();
    for (ItemStack item : carried) {
      counts.merge(item.getType().name(), item.getAmount(), Integer::sum);
    }
    counts.values().removeIf(amount -> amount == 0);
    return counts;
  }

  private static RandomGenerator fixed(double value) {
    return new RandomGenerator() {
      @Override
      public long nextLong() {
        return 0L;
      }

      @Override
      public double nextDouble() {
        return value;
      }
    };
  }
}
