package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class LumberNodeBehaviorTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000023c1");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000023c2");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);
  private static final GolemState.Anchor CHEST = GolemState.Anchor.of(CropField.WORLD, 6, 64, 6);
  private static final long NOW = 50_000L;
  private static final int AREA = 7 * 5 * 7;

  private final GolemLumberCore.Config config = new GolemLumberCore.Config();
  private final CropField field = new CropField();
  private final LumberFellings fellings = new LumberFellings();
  private final LumberRejections rejections = new LumberRejections();
  private final Set<String> stamped = new HashSet<>();
  private final List<GolemTask> posted = new ArrayList<>();
  private final List<ItemStack> carried = new ArrayList<>();
  private final List<Double> xp = new ArrayList<>();
  private final ItemStack[] chest = new ItemStack[9];
  private final NodeRecord record = NodeRecord.builder(OWNER, NodeType.LUMBER, BlockFace.UP).radii(3, 2, 3).build();
  private GolemLumberCore core;
  private LumberNodeBehavior behavior;
  private MockedStatic<LumberFeedback> feedback;
  private GolemBody body = GolemBody.IRON;
  private GolemState.Anchor homeChest;
  private int room = 64;

  @BeforeEach
  void setUp() {
    core = mock(GolemLumberCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.sweepsLeaves(OWNER)).thenReturn(true);
    level(5);
    behavior = new LumberNodeBehavior(core, fellings, rejections, new LumberActions(core, block -> stamped.contains(position(block))));
    feedback = mockStatic(LumberFeedback.class);
    for (Material log : List.of(Material.OAK_LOG, Material.SPRUCE_LOG, Material.DARK_OAK_LOG, Material.CRIMSON_STEM,
        Material.NETHER_WART_BLOCK, Material.SHROOMLIGHT)) {
      field.drops(log, () -> CropField.stacks(log, 1));
    }
    field.drops(Material.OAK_LEAVES, () -> CropField.stacks(Material.OAK_SAPLING, 1, Material.STICK, 1));
  }

  @AfterEach
  void close() {
    feedback.close();
  }

  @Test
  void itActsForTheLumberCoreAdaptationOnceASecond() {
    assertThat(behavior.policy()).isSameAs(core);
    assertThat(behavior.actIntervalTicks(golem(0), fell(0, 64, 0))).isEqualTo(20);
  }

  @Test
  void aScanPostsOneFellJobAtTheBaseOfEachNaturalTree() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    tree(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, -3, -3, 5, true);

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("fell -3,64,-3", "fell 0,64,0");
    assertThat(posted).allMatch(task -> EnumSet.allOf(GolemBody.class).stream().allMatch(task::allows));
  }

  @Test
  void megaTreesWaitForTheirLevel() {
    level(3);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    tree(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, -3, -3, 5, true);

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("fell 0,64,0");
  }

  @Test
  void buildingsAndOwnersWithoutTheCoreGetNoJobs() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    field.set(1, 65, 0, Material.OAK_PLANKS);

    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();

    field.set(1, 65, 0, Material.AIR);
    level(0);
    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();
  }

  @Test
  void bigTreesAreOfferedOnlyToBodiesThatCanFellThem() {
    level(1);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 30, false);

    behavior.scan(node(), budget(true));
    assertThat(posted).singleElement().satisfies(task -> assertThat(EnumSet.allOf(GolemBody.class)).filteredOn(task::allows).containsExactly(GolemBody.IRON));

    posted.clear();
    config.ironTreeCapByLevel = new ArrayList<>(List.of(20));
    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();
  }

  @Test
  void aScanKeepsJobsOfTreesBeingFelledAndWithdrawsJobsWhoseTreeIsGone() {
    fellings.start(CropField.WORLD, LumberTrees.survey(LumberTreesTest.oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 64)).tree(), NOW);
    GolemTask gone = fell(2, 64, 2);
    GolemTask outside = fell(9, 64, 9);
    posted.add(gone);
    posted.add(outside);

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("fell 0,64,0");
    GolemTask kept = posted.get(0);
    behavior.scan(node(), budget(true));
    assertThat(posted).containsExactly(kept);
  }

  @Test
  void theFirstActWindsUpAndLaterActsFellTheTreeTopDownAtTheLevelsRate() {
    level(2);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);

    assertThat(behavior.act(golem(0), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(logs(0, 0)).containsExactly(64, 65, 66, 67, 68);
    assertThat(fellings.get(CropField.WORLD, 0, 64, 0)).isPresent();
    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(logs(0, 0)).containsExactly(64, 65, 66, 67);
    assertThat(behavior.act(golem(2), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(logs(0, 0)).containsExactly(64, 65);
    assertThat(behavior.act(golem(3), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(behavior.act(golem(4), task)).isEqualTo(GolemActResult.DONE);

    assertThat(logs(0, 0)).isEmpty();
    assertThat(carriedCounts()).containsExactlyEntriesOf(Map.of("OAK_LOG", 5));
    assertThat(xp).containsExactly(0.6D, 1.2D, 0.6D, 0.6D);
    assertThat(fellings.pending()).isFalse();
    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AIR);
    verify(field.block(0, 68, 0), never()).breakNaturally();
    verify(field.block(0, 68, 0), never()).breakNaturally(any(ItemStack.class));
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Location.class), eq(LumberFeedback.Kind.FELL)), times(4));
  }

  @Test
  void rejectedTreesAreLookedAtAgainAfterAMinute() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    field.set(1, 65, 0, Material.OAK_PLANKS);
    behavior.scan(node(NOW), budget(true));
    field.set(1, 65, 0, Material.AIR);

    behavior.scan(node(NOW + 1_000L), budget(true));
    assertThat(posted).isEmpty();

    behavior.scan(node(NOW + LumberRejections.REJECT_MILLIS), budget(true));
    assertThat(jobs()).containsExactly("fell 0,64,0");
  }

  @Test
  void wideAndUnownedTreesAreRememberedToo() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    field.set(1, 63, 0, Material.GRASS_BLOCK).set(1, 64, 0, Material.OAK_LOG);

    behavior.scan(node(NOW), budget(true));
    assertThat(posted).isEmpty();
    assertThat(rejections.rejected(KEY, 0, 64, 0, NOW + 1_000L)).isTrue();

    rejections.forget();
    field.set(1, 64, 0, Material.AIR);
    behavior.scan(node(NOW, block -> block.getX() >= 0), budget(true));
    assertThat(posted).isEmpty();
    assertThat(rejections.rejected(KEY, 0, 64, 0, NOW + 1_000L)).isTrue();
  }

  @Test
  void aFellingNoGolemCanFinishIsDroppedByTheScan() {
    level(1);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 30, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    posted.add(task);
    config.ironTreeCapByLevel = new ArrayList<>(List.of(20));

    behavior.scan(node(NOW), budget(true));

    assertThat(fellings.pending()).isFalse();
    assertThat(posted).isEmpty();
  }

  @Test
  void aFellingWhoseLevelIsLostIsDroppedByTheScan() {
    level(4);
    tree(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 0, 0, 3, true);
    behavior.act(golem(0), fell(0, 64, 0));
    level(3);

    behavior.scan(node(NOW), budget(true));

    assertThat(fellings.pending()).isFalse();
    assertThat(posted).isEmpty();
  }

  @Test
  void aFellingStaysResumableWhileItsNodeKeepsChecking() {
    level(1);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0, NOW), task);
    behavior.act(golem(1, NOW), task);
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        field.set(x, 68, z, Material.AIR);
        field.set(x, 69, z, Material.AIR);
      }
    }

    behavior.scan(node(NOW + 50_000L), budget(false));
    behavior.scan(node(NOW + 100_000L), budget(false));
    fellings.sweep(NOW + 150_000L);

    assertThat(fellings.get(CropField.WORLD, 0, 64, 0)).isPresent();
    assertThat(behavior.act(golem(0, NOW + 150_000L), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(behavior.act(golem(1, NOW + 151_000L), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(logs(0, 0)).containsExactly(64, 65, 66);
  }

  @Test
  void aBatchWithAnyProtectedLogIsBlockedWhole() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    GolemContext denied = golem(1);
    when(denied.canBreak(any(Location.class))).thenAnswer(invocation -> invocation.<Location>getArgument(0).getBlockY() != 66);

    assertThat(behavior.act(denied, task)).isEqualTo(GolemActResult.BLOCKED);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66, 67, 68);
    assertThat(fellings.get(CropField.WORLD, 0, 64, 0)).isPresent();
    assertThat(carried).isEmpty();
  }

  @Test
  void aTreeSomeoneBuildsOntoMidFellingIsAbandoned() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    field.set(1, 67, 0, Material.OAK_PLANKS);

    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.FAILED);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66, 67, 68);
    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void aStampedLogFailsTheJobBeforeAnythingIsCut() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    stamped.add("0,66,0");

    assertThat(behavior.act(golem(0), fell(0, 64, 0))).isEqualTo(GolemActResult.FAILED);

    assertThat(fellings.pending()).isFalse();
    assertThat(logs(0, 0)).hasSize(5);
  }

  @Test
  void aCopperGolemLeavesTreesAboveItsCap() {
    level(1);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 30, false);
    body = GolemBody.COPPER;

    assertThat(behavior.act(golem(0), fell(0, 64, 0))).isEqualTo(GolemActResult.FAILED);

    body = GolemBody.IRON;
    assertThat(behavior.act(golem(0), fell(0, 64, 0))).isEqualTo(GolemActResult.WORKING);
    body = GolemBody.COPPER;
    assertThat(behavior.act(golem(1), fell(0, 64, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void cargoRoomLimitsABatch() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    room = 2;

    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.WORKING);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66);
  }

  @Test
  void aGolemThatComesBackFinishesTheSameTreeWithoutSurveyingItAgain() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    behavior.act(golem(1), task);
    for (int x = -1; x <= 1; x++) {
      for (int y = 64; y <= 69; y++) {
        for (int z = -1; z <= 1; z++) {
          if (field.type(x, y, z) == Material.OAK_LEAVES) {
            field.set(x, y, z, Material.AIR);
          }
        }
      }
    }

    assertThat(behavior.act(golem(0), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.DONE);

    assertThat(logs(0, 0)).isEmpty();
  }

  @Test
  void replantingUsesACarriedSaplingFromLevelTwo() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    carried.add(GolemTestItems.stack(Material.OAK_SAPLING, 1));
    level(1);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AIR);
    assertThat(carriedCounts()).containsEntry("OAK_SAPLING", 1);

    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    level(2);
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Material.OAK_SAPLING);
    assertThat(carriedCounts()).doesNotContainKey("OAK_SAPLING");
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Location.class), eq(LumberFeedback.Kind.PLANT)));
  }

  @Test
  void replantingTakesASaplingFromTheHomeChestWhenNoneIsCarried() {
    level(2);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    homeChest = CHEST;
    chest[3] = GolemTestItems.stack(Material.OAK_SAPLING, 2);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.OAK_SAPLING);
    assertThat(chest[3].getAmount()).isEqualTo(1);
  }

  @Test
  void leafSweepWaitsForTheLeavesToComeLooseThenCollectsTheirDrops() {
    level(3);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    field.set(-1, 69, -1, Material.OAK_LEAVES, leaves(true, 7));
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0, NOW), task);
    behavior.act(golem(1, NOW + 1_000L), task);
    behavior.act(golem(2, NOW + 2_000L), task);

    assertThat(behavior.act(golem(3, NOW + 3_000L), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(logs(0, 0)).isEmpty();
    assertThat(behavior.act(golem(4, NOW + 4_000L), task)).isEqualTo(GolemActResult.WORKING);
    field.set(1, 68, 0, Material.OAK_LEAVES, leaves(false, 7));
    field.set(0, 69, 0, Material.OAK_LEAVES, leaves(false, 7));
    field.set(0, 68, 1, Material.OAK_LEAVES, leaves(false, 3));
    field.set(-1, 68, 0, Material.OAK_LEAVES, leaves(false, 7));
    assertThat(field.type(1, 68, 0)).isEqualTo(Material.OAK_LEAVES);
    GolemContext sweeper = golem(5, NOW + 5_000L);
    when(sweeper.canBreak(any(Location.class))).thenAnswer(invocation -> invocation.<Location>getArgument(0).getBlockX() != -1);

    assertThat(behavior.act(sweeper, task)).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(1, 68, 0)).isEqualTo(Material.AIR);
    assertThat(field.type(0, 69, 0)).isEqualTo(Material.AIR);
    assertThat(field.type(-1, 69, -1)).isEqualTo(Material.OAK_LEAVES);
    assertThat(field.type(-1, 68, 0)).isEqualTo(Material.OAK_LEAVES);
    assertThat(field.type(0, 68, 1)).isEqualTo(Material.OAK_LEAVES);
    assertThat(field.type(0, 64, 0)).isEqualTo(Material.OAK_SAPLING);
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("OAK_LOG", 5, "OAK_SAPLING", 1, "STICK", 2));
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Location.class), eq(LumberFeedback.Kind.SWEEP)));
  }

  @Test
  void anOwnerWhoSwitchesLeafSweepOffLeavesTheLeavesAndFinishesOnceTheLogsAreDown() {
    level(3);
    when(core.sweepsLeaves(OWNER)).thenReturn(false);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0, NOW), task);
    behavior.act(golem(1, NOW + 1_000L), task);
    behavior.act(golem(2, NOW + 2_000L), task);
    int leaves = leafCount();

    assertThat(behavior.act(golem(3, NOW + 3_000L), task)).isEqualTo(GolemActResult.DONE);

    assertThat(logs(0, 0)).isEmpty();
    assertThat(leafCount()).isEqualTo(leaves);
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Location.class), eq(LumberFeedback.Kind.SWEEP)), never());
  }

  @Test
  void anEntropyRuneCanDoubleFelledDrops() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    GolemContext lucky = golem(1);
    when(lucky.withBonusYield(anyList())).thenAnswer(invocation -> {
      List<ItemStack> drops = new ArrayList<>(invocation.getArgument(0));
      drops.add(GolemTestItems.stack(Material.OAK_LOG, 1));
      return drops;
    });

    behavior.act(lucky, task);

    assertThat(carriedCounts()).containsExactlyEntriesOf(Map.of("OAK_LOG", 5));
    assertThat(logs(0, 0)).containsExactly(64);
  }

  @Test
  void aJobOnAnotherBaseOfAWideTrunkFails() {
    tree(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 0, 0, 3, true);

    assertThat(behavior.act(golem(0), fell(1, 64, 1))).isEqualTo(GolemActResult.FAILED);

    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void leafSweepTakesAtMostOneSliceAnActAndStopsWhenCargoIsFull() {
    level(3);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    for (int y = 66; y <= 69; y++) {
      int radius = y == 69 ? 1 : 3;
      for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
          if (field.type(x, y, z) == Material.AIR || field.type(x, y, z) == Material.OAK_LEAVES) {
            field.set(x, y, z, Material.OAK_LEAVES, leaves(false, 7));
          }
        }
      }
    }
    GolemTask task = fell(0, 64, 0);
    for (int act = 0; act <= 4; act++) {
      behavior.act(golem(act, NOW + act * 1_000L), task);
    }
    int before = leafCount();
    room = 1_000;

    assertThat(behavior.act(golem(5, NOW + 5_000L), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(leafCount()).isEqualTo(before - LumberActions.SWEEP_SLICE);

    room = 3;
    assertThat(behavior.act(golem(6, NOW + 6_000L), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(leafCount()).isEqualTo(before - LumberActions.SWEEP_SLICE - 2);

    room = 0;
    assertThat(behavior.act(golem(7, NOW + 7_000L), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(leafCount()).isEqualTo(before - LumberActions.SWEEP_SLICE - 2);

    room = 1_000;
    GolemActResult result = GolemActResult.WORKING;
    for (int act = 8; act < 20 && result == GolemActResult.WORKING; act++) {
      result = behavior.act(golem(act, NOW + act * 1_000L), task);
    }
    assertThat(result).isEqualTo(GolemActResult.DONE);
    assertThat(leafCount()).isZero();
  }

  @Test
  void mangrovesStandingOnTheirRootsAreNotReplanted() {
    level(2);
    tree(Material.MANGROVE_LOG, Material.MANGROVE_LEAVES, 0, 0, 5, false);
    field.set(0, 63, 0, Material.MANGROVE_ROOTS).set(0, 62, 0, Material.MUD);
    field.drops(Material.MANGROVE_LOG, () -> CropField.stacks(Material.MANGROVE_LOG, 1));
    carried.add(GolemTestItems.stack(Material.MANGROVE_PROPAGULE, 1));

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AIR);
    assertThat(carriedCounts()).containsEntry("MANGROVE_PROPAGULE", 1);
  }

  @Test
  void azaleaTreesAreReplantedWithAzaleas() {
    level(2);
    tree(Material.OAK_LOG, Material.AZALEA_LEAVES, 0, 0, 5, false);
    carried.add(GolemTestItems.stack(Material.OAK_SAPLING, 1));
    carried.add(GolemTestItems.stack(Material.AZALEA, 1));

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AZALEA);
    assertThat(carriedCounts()).containsEntry("OAK_SAPLING", 1).doesNotContainKey("AZALEA");
  }

  @Test
  void megaTreesReplantFourSaplingsWhenFourAreAtHand() {
    level(4);
    tree(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 0, 0, 3, true);
    carried.add(GolemTestItems.stack(Material.SPRUCE_SAPLING, 4));

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(List.of(field.type(0, 64, 0), field.type(1, 64, 0), field.type(0, 64, 1), field.type(1, 64, 1)))
        .containsOnly(Material.SPRUCE_SAPLING);
  }

  @Test
  void aSingleSaplingReplantsOnlySpeciesThatGrowAlone() {
    level(4);
    tree(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 0, 0, 3, true);
    carried.add(GolemTestItems.stack(Material.SPRUCE_SAPLING, 1));
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(List.of(field.type(0, 64, 0), field.type(1, 64, 0), field.type(0, 64, 1), field.type(1, 64, 1)))
        .containsExactly(Material.SPRUCE_SAPLING, Material.AIR, Material.AIR, Material.AIR);

    carried.clear();
    tree(Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, 0, 0, 3, true);
    carried.add(GolemTestItems.stack(Material.DARK_OAK_SAPLING, 1));
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Material.AIR);
    assertThat(carriedCounts()).containsEntry("DARK_OAK_SAPLING", 1);
  }

  @Test
  void netherFungiFellTheirCapAlongTheWayAndReplantFungus() {
    config.logsPerSecondByLevel = new ArrayList<>(List.of(1.0D, 1.0D, 1.0D, 1.0D, 1.0D));
    fungus();
    GolemTask task = fell(0, 64, 0);
    level(4);
    assertThat(behavior.act(golem(0), task)).isEqualTo(GolemActResult.FAILED);

    level(5);
    assertThat(behavior.act(golem(0), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(field.type(1, 68, 1)).isEqualTo(Material.SHROOMLIGHT);
    assertThat(carried).isEmpty();
    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.WORKING);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66);
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("NETHER_WART_BLOCK", 24, "SHROOMLIGHT", 1, "CRIMSON_STEM", 1));
    assertThat(xp).containsExactly(0.6D);
    for (int y = 66; y <= 68; y++) {
      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
          assertThat(field.type(x, y, z)).as("cap at %s,%s,%s", x, y, z).isIn(Material.AIR, Material.CRIMSON_STEM);
        }
      }
    }

    carried.add(GolemTestItems.stack(Material.CRIMSON_FUNGUS, 1));
    int act = 2;
    GolemActResult result = GolemActResult.WORKING;
    while (result == GolemActResult.WORKING && act < 10) {
      result = behavior.act(golem(act), task);
      act++;
    }
    assertThat(result).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Material.CRIMSON_FUNGUS);
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Location.class), eq(LumberFeedback.Kind.FELL_STEM)), atLeastOnce());
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Location.class), eq(LumberFeedback.Kind.PLANT_FUNGUS)));
  }

  @Test
  void jobsOutsideTheNodesAreaOrOfAnotherTypeFail() {
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 0, 0, 5, false);
    tree(Material.OAK_LOG, Material.OAK_LEAVES, 5, 0, 5, false);

    assertThat(behavior.act(golem(0), fell(5, 64, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(logs(5, 0)).hasSize(5);
    assertThat(behavior.act(golem(0), GolemTask.builder(KEY, NodeType.LUMBER, "chop", GolemTarget.block(CropField.WORLD, 0, 64, 0)).build()))
        .isEqualTo(GolemActResult.FAILED);
    assertThat(fellings.pending()).isFalse();
  }

  private GolemActResult fellAll() {
    GolemTask task = fell(0, 64, 0);
    GolemActResult result = GolemActResult.WORKING;
    for (int act = 0; act < 40 && result == GolemActResult.WORKING; act++) {
      result = behavior.act(golem(act, NOW + act * 1_000L), task);
    }
    return result;
  }

  private void tree(Material log, Material leaf, int x, int z, int height, boolean wide) {
    int size = wide ? 2 : 1;
    int top = 64 + height - 1;
    for (int dx = 0; dx < size; dx++) {
      for (int dz = 0; dz < size; dz++) {
        field.set(x + dx, 63, z + dz, Material.GRASS_BLOCK);
        for (int y = 64; y <= top; y++) {
          field.set(x + dx, y, z + dz, log);
        }
      }
    }
    for (int y = top; y <= top + 1; y++) {
      for (int lx = x - 1; lx <= x + size; lx++) {
        for (int lz = z - 1; lz <= z + size; lz++) {
          if (field.type(lx, y, lz) == Material.AIR) {
            field.set(lx, y, lz, leaf, leaves(false, 1));
          }
        }
      }
    }
  }

  private void fungus() {
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        field.set(x, 63, z, Material.CRIMSON_NYLIUM);
      }
    }
    for (int y = 64; y <= 67; y++) {
      field.set(0, y, 0, Material.CRIMSON_STEM);
    }
    for (int y = 66; y <= 68; y++) {
      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
          if (field.type(x, y, z) == Material.AIR) {
            field.set(x, y, z, Material.NETHER_WART_BLOCK);
          }
        }
      }
    }
    field.set(1, 68, 1, Material.SHROOMLIGHT);
  }

  private static Leaves leaves(boolean persistent, int distance) {
    Leaves data = mock(Leaves.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(data.isPersistent()).thenReturn(persistent);
    lenient().when(data.getDistance()).thenReturn(distance);
    lenient().when(data.getMaximumDistance()).thenReturn(7);
    return data;
  }

  private List<Integer> logs(int x, int z) {
    List<Integer> heights = new ArrayList<>();
    for (int y = 60; y <= 100; y++) {
      Material type = field.type(x, y, z);
      if (type.name().endsWith("_LOG") || type.name().endsWith("_STEM")) {
        heights.add(y);
      }
    }
    return heights;
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(OWNER)).thenReturn(level);
  }

  private int leafCount() {
    int count = 0;
    for (int y = 60; y <= 72; y++) {
      for (int x = -4; x <= 4; x++) {
        for (int z = -4; z <= 4; z++) {
          if (field.type(x, y, z) == Material.OAK_LEAVES) {
            count++;
          }
        }
      }
    }
    return count;
  }

  private NodeContext node() {
    return node(NOW);
  }

  private NodeContext node(long nowMillis) {
    return node(nowMillis, block -> true);
  }

  private NodeContext node(long nowMillis, Predicate<Block> owns) {
    NodeContext node = mock(NodeContext.class);
    lenient().when(node.owner()).thenReturn(OWNER);
    lenient().when(node.key()).thenReturn(KEY);
    lenient().when(node.record()).thenReturn(record);
    lenient().when(node.world()).thenReturn(field.world);
    lenient().when(node.nowMillis()).thenReturn(nowMillis);
    lenient().when(node.owns(any(Block.class))).thenAnswer(invocation -> owns.test(invocation.getArgument(0)));
    lenient().when(node.block(anyInt())).thenAnswer(invocation -> {
      int[] at = NodeArea.position(KEY, record, invocation.getArgument(0));
      return Optional.of(field.block(at[0], at[1], at[2]));
    });
    lenient().when(node.tasks()).thenAnswer(invocation -> List.copyOf(posted));
    lenient().when(node.task(anyString(), any(GolemTarget.class)))
        .thenAnswer(invocation -> GolemTask.builder(KEY, NodeType.LUMBER, invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(node.post(any(GolemTask.class))).thenAnswer(invocation -> posted.add(invocation.getArgument(0)));
    lenient().when(node.withdraw(any(GolemTask.class))).thenAnswer(invocation -> posted.remove(invocation.<GolemTask>getArgument(0)));
    return node;
  }

  private static ScanBudget budget(boolean room) {
    ScanBudget budget = mock(ScanBudget.class);
    lenient().when(budget.start()).thenReturn(0);
    lenient().when(budget.end()).thenReturn(AREA);
    lenient().when(budget.hasTaskRoom()).thenReturn(room);
    return budget;
  }

  private GolemContext golem(int actIndex) {
    return golem(actIndex, NOW);
  }

  private GolemContext golem(int actIndex, long nowMillis) {
    GolemContext golem = mock(GolemContext.class);
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    Mob entity = mock(Mob.class);
    lenient().when(entity.getLocation()).thenReturn(new Location(field.world, 0.5D, 64D, 1.5D));
    GolemState state = mock(GolemState.class);
    lenient().when(state.homeChest()).thenAnswer(invocation -> homeChest);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(GOLEM);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.body()).thenAnswer(invocation -> body);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(golem.handle()).thenReturn(handle);
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.golem()).thenReturn(entity);
    lenient().when(golem.node()).thenReturn(Optional.of(record));
    lenient().when(golem.world()).thenReturn(field.world);
    lenient().when(golem.nowMillis()).thenReturn(nowMillis);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.owns(any(Location.class))).thenReturn(true);
    lenient().when(golem.canBreak(any(Location.class))).thenReturn(true);
    lenient().when(golem.canPlace(any(Location.class))).thenReturn(true);
    lenient().when(golem.room()).thenAnswer(invocation -> room);
    lenient().when(golem.carriedItems()).thenAnswer(invocation -> List.copyOf(carried));
    lenient().when(golem.withBonusYield(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(golem.carry(any(ItemStack.class))).thenAnswer(invocation -> {
      carried.add(invocation.getArgument(0));
      return Optional.empty();
    });
    lenient().when(golem.take(any(), anyInt())).thenAnswer(invocation -> take(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().doAnswer(invocation -> xp.add(invocation.getArgument(0))).when(golem).workXp(anyDouble());
    lenient().when(golem.container(any(Block.class))).thenAnswer(invocation -> {
      Block block = invocation.getArgument(0);
      return homeChest != null && block.getX() == CHEST.x() && block.getY() == CHEST.y() && block.getZ() == CHEST.z()
          ? Optional.of(inventory()) : Optional.empty();
    });
    return golem;
  }

  private int take(Predicate<ItemStack> matches, int amount) {
    int taken = 0;
    Iterator<ItemStack> items = carried.iterator();
    while (items.hasNext() && taken < amount) {
      ItemStack item = items.next();
      if (!matches.test(item)) {
        continue;
      }
      int used = Math.min(item.getAmount(), amount - taken);
      taken += used;
      if (used == item.getAmount()) {
        items.remove();
      } else {
        item.setAmount(item.getAmount() - used);
      }
    }
    return taken;
  }

  private Inventory inventory() {
    Inventory inventory = mock(Inventory.class);
    lenient().when(inventory.getStorageContents()).thenAnswer(invocation -> chest.clone());
    lenient().when(inventory.getItem(anyInt())).thenAnswer(invocation -> chest[invocation.<Integer>getArgument(0)]);
    lenient().doAnswer(invocation -> {
      chest[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
      return null;
    }).when(inventory).setItem(anyInt(), any());
    return inventory;
  }

  private static GolemTask fell(int x, int y, int z) {
    return GolemTask.builder(KEY, NodeType.LUMBER, LumberNodeBehavior.FELL, GolemTarget.block(CropField.WORLD, x, y, z)).build();
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

  private Map<String, Integer> carriedCounts() {
    Map<String, Integer> counts = new TreeMap<>();
    for (ItemStack item : carried) {
      counts.merge(item.getType().name(), item.getAmount(), Integer::sum);
    }
    return counts;
  }

  private static String position(Block block) {
    return block.getX() + "," + block.getY() + "," + block.getZ();
  }
}
