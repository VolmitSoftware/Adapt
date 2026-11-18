package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemAnchor;
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
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
  private static final GolemAnchor CHEST = GolemAnchor.of(CropField.WORLD, 6, 64, 6);
  private static final long NOW = 50_000L;
  private static final int AREA = 7 * 5 * 7;

  private final GolemLumberCore.Config config = new GolemLumberCore.Config();
  private final CropField field = new CropField();
  private final List<GolemContext> created = new ArrayList<>();
  private final LumberFellings fellings = new LumberFellings();
  private final LumberRejections rejections = new LumberRejections();
  private final Set<String> stamped = new HashSet<>();
  private final List<GolemTask> posted = new ArrayList<>();
  private final List<ItemRef> carried = new ArrayList<>();
  private final List<ItemEntityRef> dropped = new ArrayList<>();
  private final Set<EntityRef> shops = new HashSet<>();
  private final List<Double> xp = new ArrayList<>();
  private final ItemRef[] chest = new ItemRef[9];
  private final NodeRecord record = NodeRecord.builder(OWNER, NodeType.LUMBER, BlockFace.UP).area(NodeCuboid.symmetric(3, 2, 3)).build();
  private GolemLumberCore core;
  private LumberNodeBehavior behavior;
  private MockedStatic<LumberFeedback> feedback;
  private MockedStatic<Tasks> scheduling;
  private MockedStatic<ProtectionProbes> probes;
  private GolemBody body = GolemBody.IRON;
  private GolemAnchor homeChest;
  private int room = 64;

  @BeforeEach
  void setUp() {
    core = mock(GolemLumberCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.sweepsLeaves(OWNER)).thenReturn(true);
    level(5);
    behavior = new LumberNodeBehavior(core, fellings, rejections, new LumberActions(core, block -> stamped.contains(position(block))));
    feedback = mockStatic(LumberFeedback.class);
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(EntityRef.class))).thenReturn(true);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(Loc.class), anyDouble(), anyDouble())).thenReturn(true);
    AdaptPorts.install(EntityMarkerPort.class, new EntityMarkerPort() {
      @Override
      public boolean marked(EntityRef entity, String marker) {
        return marker.equals("shopitem") && shops.contains(entity);
      }
    });
    probes = mockStatic(ProtectionProbes.class);
    probes.when(() -> ProtectionProbes.attemptEntityItemPickup(any(LivingRef.class), any(ItemEntityRef.class), anyInt())).thenReturn(true);
    lenient().when(field.world.entitiesIn(any(Box.class), any())).thenAnswer(invocation -> {
      Box bounds = invocation.getArgument(0);
      Predicate<EntityRef> filter = invocation.getArgument(1);
      List<EntityRef> found = new ArrayList<>();
      for (ItemEntityRef item : dropped) {
        Loc at = item.location();
        if (inside(bounds, at) && filter.test(item)) {
          found.add(item);
        }
      }
      return found;
    });
    for (Id log : List.of(Id.minecraft("oak_log"), Id.minecraft("spruce_log"), Id.minecraft("dark_oak_log"), Id.minecraft("crimson_stem"),
        Id.minecraft("nether_wart_block"), Id.minecraft("shroomlight"))) {
      field.drops(log, () -> CropField.stacks(log, 1));
    }
    field.drops(Id.minecraft("oak_leaves"), () -> CropField.stacks(Id.minecraft("oak_sapling"), 1, Id.minecraft("stick"), 1));
  }

  @AfterEach
  void close() {
    AdaptPorts.uninstall(EntityMarkerPort.class);
    probes.close();
    scheduling.close();
    feedback.close();
  }

  @Test
  void itActsForTheLumberCoreAdaptationOnceASecond() {
    assertThat(behavior.policy()).isSameAs(core);
    assertThat(behavior.actIntervalTicks(golem(0), fell(0, 64, 0))).isEqualTo(20);
  }

  @Test
  void aScanPostsOneFellJobAtTheBaseOfEachNaturalTree() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    tree(Id.minecraft("spruce_log"), Id.minecraft("spruce_leaves"), -3, -3, 5, true);

    behavior.scan(node(), budget(true));
    assertThat(posted).hasSize(1);
    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("fell -3,64,-3", "fell 0,64,0");
    assertThat(posted).allMatch(task -> EnumSet.allOf(GolemBody.class).stream().allMatch(task::allows));
  }

  @Test
  void megaTreesWaitForTheirLevel() {
    level(3);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    tree(Id.minecraft("spruce_log"), Id.minecraft("spruce_leaves"), -3, -3, 5, true);

    behavior.scan(node(), budget(true));
    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("fell 0,64,0");
  }

  @Test
  void buildingsAndOwnersWithoutTheCoreGetNoJobs() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    field.set(1, 65, 0, Id.minecraft("oak_planks"));

    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();

    field.set(1, 65, 0, Id.minecraft("air"));
    level(0);
    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();
  }

  @Test
  void bigTreesAreOfferedOnlyToBodiesThatCanFellThem() {
    level(1);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 30, false);

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
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
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
    assertThat(fellings.pending()).isTrue();
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
    verify(field.block(0, 68, 0), never()).breakNaturally(isNull());
    verify(field.block(0, 68, 0), never()).breakNaturally(any(ItemRef.class));
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Loc.class), eq(LumberFeedback.Kind.FELL)), times(4));
  }

  @Test
  void rejectedTreesAreLookedAtAgainAfterAMinute() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    field.set(1, 65, 0, Id.minecraft("oak_planks"));
    behavior.scan(node(NOW), budget(true));
    field.set(1, 65, 0, Id.minecraft("air"));

    behavior.scan(node(NOW + 1_000L), budget(true));
    assertThat(posted).isEmpty();

    behavior.scan(node(NOW + LumberRejections.REJECT_MILLIS), budget(true));
    assertThat(jobs()).containsExactly("fell 0,64,0");
  }

  @Test
  void wideTreesAreRememberedAndUnavailableRegionsRemainRetryable() {
    level(3);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    field.set(1, 63, 0, Id.minecraft("grass_block")).set(1, 64, 0, Id.minecraft("oak_log")).set(1, 65, 0, Id.minecraft("oak_log")).set(1, 66, 0, Id.minecraft("oak_log"));

    behavior.scan(node(NOW), budget(true));
    assertThat(posted).isEmpty();
    assertThat(rejections.rejected(KEY, 0, 64, 0, NOW + 1_000L)).isTrue();

    rejections.forget();
    field.set(1, 64, 0, Id.minecraft("air")).set(1, 65, 0, Id.minecraft("air")).set(1, 66, 0, Id.minecraft("air"));
    behavior.scan(node(NOW, block -> block.x() >= 0), budget(true));
    assertThat(posted).isEmpty();
    assertThat(rejections.rejected(KEY, 0, 64, 0, NOW + 1_000L)).isFalse();
  }

  @Test
  void aFellingNoGolemCanFinishIsDroppedByTheScan() {
    level(1);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 30, false);
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
    tree(Id.minecraft("spruce_log"), Id.minecraft("spruce_leaves"), 0, 0, 3, true);
    behavior.act(golem(0), fell(0, 64, 0));
    level(3);

    behavior.scan(node(NOW), budget(true));

    assertThat(fellings.pending()).isFalse();
    assertThat(posted).isEmpty();
  }

  @Test
  void aFellingStaysResumableWhileItsNodeKeepsChecking() {
    level(1);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0, NOW), task);
    behavior.act(golem(1, NOW), task);
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        field.set(x, 68, z, Id.minecraft("air"));
        field.set(x, 69, z, Id.minecraft("air"));
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
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    GolemContext denied = golem(1);
    when(denied.canBreak(any(Loc.class))).thenAnswer(invocation -> invocation.<Loc>getArgument(0).blockY() != 66);

    assertThat(behavior.act(denied, task)).isEqualTo(GolemActResult.BLOCKED);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66, 67, 68);
    assertThat(fellings.get(CropField.WORLD, 0, 64, 0)).isPresent();
    assertThat(carried).isEmpty();
  }

  @Test
  void aTreeSomeoneBuildsOntoMidFellingIsAbandoned() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    field.set(1, 67, 0, Id.minecraft("oak_planks"));

    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.FAILED);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66, 67, 68);
    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void aStampedLogFailsTheJobBeforeAnythingIsCut() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    stamped.add("0,66,0");

    assertThat(behavior.act(golem(0), fell(0, 64, 0))).isEqualTo(GolemActResult.FAILED);

    assertThat(fellings.pending()).isFalse();
    assertThat(logs(0, 0)).hasSize(5);
  }

  @Test
  void aCopperGolemLeavesTreesAboveItsCap() {
    level(1);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 30, false);
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
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    room = 2;

    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.WORKING);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66);
  }

  @Test
  void aGolemThatComesBackFinishesTheSameTreeWithoutSurveyingItAgain() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    behavior.act(golem(1), task);
    for (int x = -1; x <= 1; x++) {
      for (int y = 64; y <= 69; y++) {
        for (int z = -1; z <= 1; z++) {
          if (Id.minecraft("oak_leaves").equals(field.type(x, y, z))) {
            field.set(x, y, z, Id.minecraft("air"));
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
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    carried.add(GolemTestItems.stack(Id.minecraft("oak_sapling"), 1));
    level(1);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
    assertThat(carriedCounts()).containsEntry("OAK_SAPLING", 1);

    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    level(2);
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("oak_sapling"));
    assertThat(carriedCounts()).doesNotContainKey("OAK_SAPLING");
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Loc.class), eq(LumberFeedback.Kind.PLANT)));
  }

  @Test
  void replantingTakesASaplingFromTheHomeChestWhenNoneIsCarried() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    homeChest = CHEST;
    chest[3] = GolemTestItems.stack(Id.minecraft("oak_sapling"), 2);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("oak_sapling"));
    assertThat(chest[3].amount()).isEqualTo(1);
    BlockRef home = field.block(CHEST.x(), CHEST.y(), CHEST.z());
    assertThat(created).anySatisfy(golem -> verify(golem).visit(home));
  }

  @Test
  void replantingCollectsOnlyOneMatchingSaplingFromTheGround() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    ItemEntityRef oak = dropped(Id.minecraft("oak_sapling"), 3, 1, 64, 0);
    ItemEntityRef birch = dropped(Id.minecraft("birch_sapling"), 2, 0, 64, 1);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("oak_sapling"));
    assertThat(oak.item().amount()).isEqualTo(2);
    assertThat(birch.item().amount()).isEqualTo(2);
    assertThat(carriedCounts()).doesNotContainKey("OAK_SAPLING");
    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void aSaplingThatDropsAfterFellingIsReplantedWithoutWaitingForTheAreaCursor() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(fellings.pending()).isTrue();
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
    ScanBudget knownBase = mock(ScanBudget.class);
    lenient().when(knownBase.indices()).thenReturn(IntStream.range(0, AREA).filter(index -> {
      int[] position = NodeArea.position(KEY, record, index);
      return position[0] == 0 && position[1] == 64 && position[2] == 0;
    }).toArray());
    when(knownBase.hasTaskRoom()).thenReturn(true);

    behavior.scan(node(NOW + 8_000L), knownBase);
    assertThat(posted).isEmpty();
    ItemEntityRef sapling = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    behavior.scan(node(NOW + 10_000L), knownBase);
    assertThat(jobs()).containsExactly("fell 0,64,0");

    assertThat(behavior.act(golem(0, NOW + 10_000L), posted.getFirst())).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("oak_sapling"));
    assertThat(dropped).doesNotContain(sapling);
    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void anUnavailableSaplingReleasesTheGolemBetweenRetriesAndExpires() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    GolemTask task = fell(0, 64, 0);
    assertThat(behavior.act(golem(0, NOW + 10_000L), task)).isEqualTo(GolemActResult.FAILED);
    assertThat(fellings.pending()).isTrue();

    behavior.scan(node(NOW + 12_000L), budget(true));
    assertThat(posted).isEmpty();
    fellings.get(CropField.WORLD, 0, 64, 0).orElseThrow().touch(NOW + 304_000L);
    fellings.sweep(NOW + 304_000L);
    dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    behavior.scan(node(NOW + 305_000L), budget(true));

    assertThat(fellings.pending()).isFalse();
    assertThat(posted).isEmpty();
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
  }

  @Test
  void aReplantRetryDoesNotReplaceABlockOrConsumeSaplingsWhenTheSiteChanges() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    field.set(0, 64, 0, Id.minecraft("stone"));
    ItemEntityRef sapling = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);

    assertThat(behavior.act(golem(0, NOW + 10_000L), fell(0, 64, 0))).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("stone"));
    assertThat(dropped).contains(sapling);
    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void replantingIgnoresOtherOwnersItemsAndItemsWithPickupRestrictions() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    ItemEntityRef owned = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    when(owned.owner()).thenReturn(GOLEM);
    ItemEntityRef thrown = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    when(thrown.thrower()).thenReturn(GOLEM);
    ItemEntityRef delayed = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    when(delayed.pickupDelay()).thenReturn(10);
    ItemEntityRef restricted = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    when(restricted.canMobPickup()).thenReturn(false);
    ItemEntityRef shop = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    shops.add(shop);
    ItemEntityRef outside = dropped(Id.minecraft("oak_sapling"), 1, 30, 64, 0);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
    assertThat(dropped).containsExactly(owned, thrown, delayed, restricted, shop, outside);
    assertThat(carriedCounts()).doesNotContainKey("OAK_SAPLING");
  }

  @Test
  void replantingHonorsPickupCancellationAndClaims() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    ItemEntityRef sapling = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    probes.when(() -> ProtectionProbes.attemptEntityItemPickup(any(LivingRef.class), eq(sapling), anyInt())).thenReturn(false);
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
    probes.when(() -> ProtectionProbes.attemptEntityItemPickup(any(LivingRef.class), eq(sapling), anyInt())).thenReturn(true);
    GolemContext golem = golem(0, NOW + 10_000L);
    when(golem.canInteract(any(Loc.class))).thenReturn(false);

    assertThat(behavior.act(golem, fell(0, 64, 0))).isEqualTo(GolemActResult.FAILED);

    assertThat(dropped).contains(sapling);
    assertThat(carriedCounts()).doesNotContainKey("OAK_SAPLING");
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
  }

  @Test
  void replantingDoesNotCollectAcrossFoliaRegionOwnership() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    ItemEntityRef sapling = dropped(Id.minecraft("oak_sapling"), 1, 1, 64, 0);
    scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(Loc.class), anyDouble(), anyDouble())).thenReturn(false);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(dropped).contains(sapling);
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
    verify(field.world, never()).entitiesIn(any(Box.class), any());
  }

  @Test
  void pairedTreesCombineCarriedStockAndGroundDropsWithoutTakingExtra() {
    level(4);
    tree(Id.minecraft("dark_oak_log"), Id.minecraft("dark_oak_leaves"), 0, 0, 3, true);
    carried.add(GolemTestItems.stack(Id.minecraft("dark_oak_sapling"), 1));
    homeChest = CHEST;
    chest[3] = GolemTestItems.stack(Id.minecraft("dark_oak_sapling"), 1);
    ItemEntityRef saplings = dropped(Id.minecraft("dark_oak_sapling"), 3, 2, 64, 0);

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(List.of(field.type(0, 64, 0), field.type(1, 64, 0), field.type(0, 64, 1), field.type(1, 64, 1)))
        .containsOnly(Id.minecraft("dark_oak_sapling"));
    assertThat(saplings.item().amount()).isEqualTo(1);
    assertThat(chest[3]).isNull();
    assertThat(carriedCounts()).doesNotContainKey("DARK_OAK_SAPLING");
  }

  @Test
  void leafSweepWaitsForTheLeavesToComeLooseThenCollectsTheirDrops() {
    level(3);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    field.set(-1, 69, -1, Id.minecraft("oak_leaves"), leaves(false, 7));
    stamped.add("-1,69,-1");
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0, NOW), task);
    behavior.act(golem(1, NOW + 1_000L), task);
    behavior.act(golem(2, NOW + 2_000L), task);

    assertThat(behavior.act(golem(3, NOW + 3_000L), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(logs(0, 0)).isEmpty();
    assertThat(behavior.act(golem(4, NOW + 4_000L), task)).isEqualTo(GolemActResult.WORKING);
    field.set(1, 68, 0, Id.minecraft("oak_leaves"), leaves(true, 7));
    field.set(0, 69, 0, Id.minecraft("oak_leaves"), leaves(false, 7));
    field.set(0, 68, 1, Id.minecraft("oak_leaves"), leaves(false, 3));
    field.set(-1, 68, 0, Id.minecraft("oak_leaves"), leaves(false, 7));
    assertThat(field.type(1, 68, 0)).isEqualTo(Id.minecraft("oak_leaves"));
    GolemContext sweeper = golem(5, NOW + 5_000L);
    when(sweeper.canBreak(any(Loc.class))).thenAnswer(invocation -> invocation.<Loc>getArgument(0).blockX() != -1);

    assertThat(behavior.act(sweeper, task)).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(1, 68, 0)).isEqualTo(Id.minecraft("air"));
    assertThat(field.type(0, 69, 0)).isEqualTo(Id.minecraft("air"));
    assertThat(field.type(-1, 69, -1)).isEqualTo(Id.minecraft("oak_leaves"));
    assertThat(field.type(-1, 68, 0)).isEqualTo(Id.minecraft("oak_leaves"));
    assertThat(field.type(0, 68, 1)).isEqualTo(Id.minecraft("oak_leaves"));
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("oak_sapling"));
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("OAK_LOG", 5, "OAK_SAPLING", 1, "STICK", 2));
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Loc.class), eq(LumberFeedback.Kind.SWEEP)));
  }

  @Test
  void aGeneratedTreeWithPersistentLeavesGetsAJobAndIsFelled() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    for (int y = 68; y <= 69; y++) {
      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
          if (Id.minecraft("oak_leaves").equals(field.type(x, y, z))) {
            field.set(x, y, z, Id.minecraft("oak_leaves"), leaves(true, 1));
          }
        }
      }
    }

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("fell 0,64,0");
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(logs(0, 0)).isEmpty();
  }

  @Test
  void anOddTreeIsFelledWithoutLeavingWoodBehind() {
    level(1);
    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        field.set(x, 63, z, Id.minecraft("coarse_dirt"));
      }
    }
    for (int y = 64; y <= 69; y++) {
      field.set(0, y, 0, y == 66 ? Id.minecraft("oak_wood") : Id.minecraft("oak_log"));
    }
    field.set(1, 64, 0, Id.minecraft("oak_wood")).set(-1, 64, 1, Id.minecraft("oak_log")).set(1, 70, 1, Id.minecraft("stripped_spruce_wood"))
        .set(2, 71, 2, Id.minecraft("spruce_wood")).set(-1, 70, -1, Id.minecraft("dark_oak_wood"));
    for (int y = 69; y <= 72; y++) {
      for (int x = -2; x <= 2; x++) {
        for (int z = -2; z <= 2; z++) {
          if (Id.minecraft("air").equals(field.type(x, y, z))) {
            field.set(x, y, z, Id.minecraft("oak_leaves"), leaves(true, 2));
          }
        }
      }
    }

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("fell 0,64,0");
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    for (int y = 60; y <= 75; y++) {
      for (int x = -3; x <= 3; x++) {
        for (int z = -3; z <= 3; z++) {
          assertThat(TreeBlocks.trunk(field.type(x, y, z), false)).as("wood left at %s,%s,%s", x, y, z).isFalse();
        }
      }
    }
    assertThat(xp).hasSize(11).allMatch(amount -> amount == 0.6D);
  }

  @Test
  void anOwnerWhoSwitchesLeafSweepOffLeavesTheLeavesAndFinishesOnceTheLogsAreDown() {
    level(3);
    when(core.sweepsLeaves(OWNER)).thenReturn(false);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0, NOW), task);
    behavior.act(golem(1, NOW + 1_000L), task);
    behavior.act(golem(2, NOW + 2_000L), task);
    int leaves = leafCount();

    assertThat(behavior.act(golem(3, NOW + 3_000L), task)).isEqualTo(GolemActResult.DONE);

    assertThat(logs(0, 0)).isEmpty();
    assertThat(leafCount()).isEqualTo(leaves);
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Loc.class), eq(LumberFeedback.Kind.SWEEP)), never());
  }

  @Test
  void anEntropyRuneCanDoubleFelledDrops() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    GolemTask task = fell(0, 64, 0);
    behavior.act(golem(0), task);
    GolemContext lucky = golem(1);
    when(lucky.withBonusYield(anyList())).thenAnswer(invocation -> {
      List<ItemRef> drops = new ArrayList<>(invocation.getArgument(0));
      drops.add(GolemTestItems.stack(Id.minecraft("oak_log"), 1));
      return drops;
    });

    behavior.act(lucky, task);

    assertThat(carriedCounts()).containsExactlyEntriesOf(Map.of("OAK_LOG", 5));
    assertThat(logs(0, 0)).containsExactly(64);
  }

  @Test
  void aJobOnAnotherBaseOfAWideTrunkFails() {
    tree(Id.minecraft("spruce_log"), Id.minecraft("spruce_leaves"), 0, 0, 3, true);

    assertThat(behavior.act(golem(0), fell(1, 64, 1))).isEqualTo(GolemActResult.FAILED);

    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void leafSweepTakesAtMostOneSliceAnActAndStopsWhenCargoIsFull() {
    level(3);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    for (int y = 66; y <= 69; y++) {
      int radius = y == 69 ? 1 : 3;
      for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
          if (Id.minecraft("air").equals(field.type(x, y, z)) || Id.minecraft("oak_leaves").equals(field.type(x, y, z))) {
            field.set(x, y, z, Id.minecraft("oak_leaves"), leaves(false, 7));
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
    tree(Id.minecraft("mangrove_log"), Id.minecraft("mangrove_leaves"), 0, 0, 5, false);
    field.set(0, 63, 0, Id.minecraft("mangrove_roots")).set(0, 62, 0, Id.minecraft("mud"));
    field.drops(Id.minecraft("mangrove_log"), () -> CropField.stacks(Id.minecraft("mangrove_log"), 1));
    carried.add(GolemTestItems.stack(Id.minecraft("mangrove_propagule"), 1));

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
    assertThat(carriedCounts()).containsEntry("MANGROVE_PROPAGULE", 1);
  }

  @Test
  void azaleaTreesAreReplantedWithAzaleas() {
    level(2);
    tree(Id.minecraft("oak_log"), Id.minecraft("azalea_leaves"), 0, 0, 5, false);
    carried.add(GolemTestItems.stack(Id.minecraft("oak_sapling"), 1));
    carried.add(GolemTestItems.stack(Id.minecraft("azalea"), 1));

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("azalea"));
    assertThat(carriedCounts()).containsEntry("OAK_SAPLING", 1).doesNotContainKey("AZALEA");
  }

  @Test
  void megaTreesReplantFourSaplingsWhenFourAreAtHand() {
    level(4);
    tree(Id.minecraft("spruce_log"), Id.minecraft("spruce_leaves"), 0, 0, 3, true);
    carried.add(GolemTestItems.stack(Id.minecraft("spruce_sapling"), 4));

    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);

    assertThat(List.of(field.type(0, 64, 0), field.type(1, 64, 0), field.type(0, 64, 1), field.type(1, 64, 1)))
        .containsOnly(Id.minecraft("spruce_sapling"));
  }

  @Test
  void aSingleSaplingReplantsOnlySpeciesThatGrowAlone() {
    level(4);
    tree(Id.minecraft("spruce_log"), Id.minecraft("spruce_leaves"), 0, 0, 3, true);
    carried.add(GolemTestItems.stack(Id.minecraft("spruce_sapling"), 1));
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(List.of(field.type(0, 64, 0), field.type(1, 64, 0), field.type(0, 64, 1), field.type(1, 64, 1)))
        .containsExactly(Id.minecraft("spruce_sapling"), Id.minecraft("air"), Id.minecraft("air"), Id.minecraft("air"));

    carried.clear();
    tree(Id.minecraft("dark_oak_log"), Id.minecraft("dark_oak_leaves"), 0, 0, 3, true);
    carried.add(GolemTestItems.stack(Id.minecraft("dark_oak_sapling"), 1));
    assertThat(fellAll()).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("air"));
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
    assertThat(field.type(1, 68, 1)).isEqualTo(Id.minecraft("shroomlight"));
    assertThat(carried).isEmpty();
    assertThat(behavior.act(golem(1), task)).isEqualTo(GolemActResult.WORKING);

    assertThat(logs(0, 0)).containsExactly(64, 65, 66);
    assertThat(carriedCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("NETHER_WART_BLOCK", 24, "SHROOMLIGHT", 1, "CRIMSON_STEM", 1));
    assertThat(xp).containsExactly(0.6D);
    for (int y = 66; y <= 68; y++) {
      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
          assertThat(field.type(x, y, z)).as("cap at %s,%s,%s", x, y, z).isIn(Id.minecraft("air"), Id.minecraft("crimson_stem"));
        }
      }
    }

    carried.add(GolemTestItems.stack(Id.minecraft("crimson_fungus"), 1));
    int act = 2;
    GolemActResult result = GolemActResult.WORKING;
    while (result == GolemActResult.WORKING && act < 10) {
      result = behavior.act(golem(act), task);
      act++;
    }
    assertThat(result).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(0, 64, 0)).isEqualTo(Id.minecraft("crimson_fungus"));
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Loc.class), eq(LumberFeedback.Kind.FELL_STEM)), atLeastOnce());
    feedback.verify(() -> LumberFeedback.play(eq(core), any(Loc.class), eq(LumberFeedback.Kind.PLANT_FUNGUS)));
  }

  @Test
  void jobsOutsideTheNodesAreaOrOfAnotherTypeFail() {
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 0, 0, 5, false);
    tree(Id.minecraft("oak_log"), Id.minecraft("oak_leaves"), 5, 0, 5, false);

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

  private void tree(Id log, Id leaf, int x, int z, int height, boolean wide) {
    int size = wide ? 2 : 1;
    int top = 64 + height - 1;
    for (int dx = 0; dx < size; dx++) {
      for (int dz = 0; dz < size; dz++) {
        field.set(x + dx, 63, z + dz, Id.minecraft("grass_block"));
        for (int y = 64; y <= top; y++) {
          field.set(x + dx, y, z + dz, log);
        }
      }
    }
    for (int y = top; y <= top + 1; y++) {
      for (int lx = x - 1; lx <= x + size; lx++) {
        for (int lz = z - 1; lz <= z + size; lz++) {
          if (Id.minecraft("air").equals(field.type(lx, y, lz))) {
            field.set(lx, y, lz, leaf, leaves(false, 1));
          }
        }
      }
    }
  }

  private void fungus() {
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        field.set(x, 63, z, Id.minecraft("crimson_nylium"));
      }
    }
    for (int y = 64; y <= 67; y++) {
      field.set(0, y, 0, Id.minecraft("crimson_stem"));
    }
    for (int y = 66; y <= 68; y++) {
      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
          if (Id.minecraft("air").equals(field.type(x, y, z))) {
            field.set(x, y, z, Id.minecraft("nether_wart_block"));
          }
        }
      }
    }
    field.set(1, 68, 1, Id.minecraft("shroomlight"));
  }

  private static BlockStateRef leaves(boolean persistent, int distance) {
    return CropField.leaves(persistent, distance);
  }

  private List<Integer> logs(int x, int z) {
    List<Integer> heights = new ArrayList<>();
    for (int y = 60; y <= 100; y++) {
      Id type = field.type(x, y, z);
      if (type.legacyName().endsWith("_LOG") || type.legacyName().endsWith("_STEM")) {
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
          if (Id.minecraft("oak_leaves").equals(field.type(x, y, z))) {
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

  private NodeContext node(long nowMillis, Predicate<BlockRef> owns) {
    NodeContext node = mock(NodeContext.class);
    lenient().when(node.owner()).thenReturn(OWNER);
    lenient().when(node.key()).thenReturn(KEY);
    lenient().when(node.record()).thenReturn(record);
    lenient().when(node.world()).thenReturn(field.world);
    lenient().when(node.nowMillis()).thenReturn(nowMillis);
    lenient().when(node.owns(any(BlockRef.class))).thenAnswer(invocation -> owns.test(invocation.getArgument(0)));
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
    lenient().when(budget.indices()).thenReturn(IntStream.range(0, AREA).toArray());
    lenient().when(budget.hasTaskRoom()).thenReturn(room);
    return budget;
  }

  private GolemContext golem(int actIndex) {
    return golem(actIndex, NOW);
  }

  private GolemContext golem(int actIndex, long nowMillis) {
    GolemContext golem = mock(GolemContext.class);
    created.add(golem);
    PlayerRef owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    MobRef entity = mock(MobRef.class);
    lenient().when(entity.location()).thenReturn(new Loc(field.world, 0.5D, 64D, 1.5D));
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
    lenient().when(golem.owns(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canBreak(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canPlace(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canInteract(any(Loc.class))).thenReturn(true);
    lenient().when(golem.room()).thenAnswer(invocation -> room);
    lenient().when(golem.carriedItems()).thenAnswer(invocation -> List.copyOf(carried));
    lenient().when(golem.withBonusYield(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(golem.carry(any(ItemRef.class))).thenAnswer(invocation -> {
      carried.add(invocation.getArgument(0));
      return Optional.empty();
    });
    lenient().when(golem.take(any(), anyInt())).thenAnswer(invocation -> take(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().doAnswer(invocation -> xp.add(invocation.getArgument(0))).when(golem).workXp(anyDouble());
    lenient().when(golem.container(any(BlockRef.class))).thenAnswer(invocation -> {
      BlockRef block = invocation.getArgument(0);
      return homeChest != null && block.x() == CHEST.x() && block.y() == CHEST.y() && block.z() == CHEST.z()
          ? Optional.of(inventory()) : Optional.empty();
    });
    return golem;
  }

  private int take(Predicate<ItemRef> matches, int amount) {
    int taken = 0;
    Iterator<ItemRef> items = carried.iterator();
    while (items.hasNext() && taken < amount) {
      ItemRef item = items.next();
      if (!matches.test(item)) {
        continue;
      }
      int used = Math.min(item.amount(), amount - taken);
      taken += used;
      if (used == item.amount()) {
        items.remove();
      } else {
        item.setAmount(item.amount() - used);
      }
    }
    return taken;
  }

  private InventoryRef inventory() {
    InventoryRef inventory = mock(InventoryRef.class);
    lenient().when(inventory.contents()).thenAnswer(invocation -> Arrays.asList(chest.clone()));
    lenient().when(inventory.item(anyInt())).thenAnswer(invocation -> chest[invocation.<Integer>getArgument(0)]);
    lenient().doAnswer(invocation -> {
      chest[invocation.<Integer>getArgument(0)] = null;
      return null;
    }).when(inventory).clear(anyInt());
    lenient().doAnswer(invocation -> {
      chest[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
      return null;
    }).when(inventory).setItem(anyInt(), any());
    return inventory;
  }

  private ItemEntityRef dropped(Id type, int amount, int x, int y, int z) {
    ItemEntityRef item = mock(ItemEntityRef.class, withSettings().strictness(Strictness.LENIENT));
    AtomicReference<ItemRef> stack = new AtomicReference<>(GolemTestItems.stack(type, amount));
    lenient().when(item.location()).thenAnswer(invocation -> new Loc(field.world, x + 0.5D, y + 0.5D, z + 0.5D));
    lenient().when(item.item()).thenAnswer(invocation -> stack.get());
    lenient().when(item.isValid()).thenAnswer(invocation -> dropped.contains(item));
    lenient().when(item.canPlayerPickup()).thenReturn(true);
    lenient().when(item.canMobPickup()).thenReturn(true);
    lenient().doAnswer(invocation -> {
      stack.set(invocation.getArgument(0));
      return null;
    }).when(item).setItem(any(ItemRef.class));
    lenient().doAnswer(invocation -> {
      dropped.remove(item);
      return null;
    }).when(item).remove();
    dropped.add(item);
    return item;
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
    for (ItemRef item : carried) {
      counts.merge(item.type().legacyName(), item.amount(), Integer::sum);
    }
    return counts;
  }

  private static boolean inside(Box box, Loc at) {
    return at.x() >= box.minX() && at.x() < box.maxX() && at.y() >= box.minY() && at.y() < box.maxY()
        && at.z() >= box.minZ() && at.z() < box.maxZ();
  }

  private static String position(BlockRef block) {
    return block.x() + "," + block.y() + "," + block.z();
  }
}
