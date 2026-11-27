package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemActResult;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarvesterCoreBehaviorTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000022d1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000022d2");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000022d3");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);
  private static final GolemAnchor CHEST = GolemAnchor.of(CropField.WORLD, 5, 64, 5);
  private static final long NOW = 70_000L;

  private final GolemHarvesterCore.Config config = new GolemHarvesterCore.Config();
  private final CropField field = new CropField();
  private final HarvestSpots spots = new HarvestSpots();
  private final ItemRef[] chest = new ItemRef[27];
  private final List<NodeIndex.Entry> nearby = new ArrayList<>();
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(4, 1, 4)).build();
  private GolemHarvesterCore core;
  private HarvesterCoreBehavior behavior;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<HarvestFeedback> feedback;
  private GolemAnchor homeChest = CHEST;
  private int channels = ChannelMask.NONE;

  @BeforeEach
  void setUp() {
    core = mock(GolemHarvesterCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.crops()).thenReturn(HarvestCrops.parse(config.crops));
    lenient().when(core.tendReady(GOLEM, NOW)).thenReturn(true);
    lenient().when(core.tends(OWNER)).thenReturn(true);
    lenient().when(core.tills(OWNER)).thenReturn(true);
    level(5);
    behavior = new HarvesterCoreBehavior(core, spots, new HarvestActions(core, ThreadLocalRandom::current));
    NodeIndex index = mock(NodeIndex.class);
    lenient().when(index.near(eq(CropField.WORLD), anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> List.copyOf(nearby));
    NodeService nodes = mock(NodeService.class);
    lenient().when(nodes.index()).thenReturn(index);
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    feedback = mockStatic(HarvestFeedback.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(nodes));
    spots.scanned(KEY, record, NOW);
  }

  @AfterEach
  void close() {
    feedback.close();
    runtime.close();
  }

  @Test
  void itWorksForTheHarvesterCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(core);
  }

  @Test
  void aGolemCarryingAWantedSeedIsSentToTheNearestEmptyFarmland() {
    node();
    farmland(3, 63, 3);
    farmland(1, 63, 0);

    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEqualTo("plant 1,63,0");
    assertThat(seek(golem(1, List.of()))).isEmpty();
    assertThat(seek(golem(1, seeds(Id.minecraft("melon_seeds"))))).isEmpty();
  }

  @Test
  void tendingNeedsTheCooldownAndBoneMealInTheLinkedHomeChest() {
    node();
    young(2, 64, 2);
    boneMeal(5);

    assertThat(seek(golem(1, List.of()))).isEqualTo("tend 2,64,2");

    when(core.tendReady(GOLEM, NOW)).thenReturn(false);
    assertThat(seek(golem(1, List.of()))).isEmpty();

    when(core.tendReady(GOLEM, NOW)).thenReturn(true);
    boneMeal(0);
    assertThat(seek(golem(1, List.of()))).isEmpty();

    boneMeal(5);
    homeChest = null;
    assertThat(seek(golem(1, List.of()))).isEmpty();
  }

  @Test
  void belowTheirLevelsTendingAndPlantingFindNothing() {
    node();
    farmland(1, 63, 0);
    young(2, 64, 2);
    boneMeal(5);

    level(3);
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEqualTo("tend 2,64,2");
    level(2);
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEmpty();
  }

  @Test
  void anOwnerWhoSwitchesTendingOrTillingOffGetsNeitherJob() {
    node();
    farmland(1, 63, 0);
    young(2, 64, 2);
    boneMeal(5);

    when(core.tills(OWNER)).thenReturn(false);
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEqualTo("tend 2,64,2");

    when(core.tends(OWNER)).thenReturn(false);
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEmpty();

    when(core.tills(OWNER)).thenReturn(true);
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEqualTo("plant 1,63,0");
  }

  @Test
  void aTendOrPlantJobFailsOnceTheOwnerSwitchesItOff() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    field.set(1, 63, 0, Id.minecraft("farmland"));
    boneMeal(5);
    when(core.tends(OWNER)).thenReturn(false);
    when(core.tills(OWNER)).thenReturn(false);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1, seeds(Id.minecraft("wheat_seeds"))), task("plant", 1, 63, 0))).isEqualTo(GolemActResult.FAILED);

    assertThat(chest[4].amount()).isEqualTo(5);
    assertThat(field.age(2, 64, 2)).isEqualTo(2);
    assertThat(field.type(1, 64, 0)).isEqualTo(Id.minecraft("air"));
  }

  @Test
  void skippedSpotsAndSpotsBeyondTheWorkRadiusArePassedOver() {
    node();
    farmland(1, 63, 0);
    farmland(3, 63, 3);
    GolemContext golem = golem(1, seeds(Id.minecraft("wheat_seeds")));
    when(golem.skips(any(GolemTask.class))).thenAnswer(invocation -> invocation.<GolemTask>getArgument(0).target().blockX() == 1);

    assertThat(seek(golem)).isEqualTo("plant 3,63,3");

    when(golem.inWorkRadius(any(Loc.class))).thenAnswer(invocation -> invocation.<Loc>getArgument(0).blockX() == 1);
    assertThat(seek(golem)).isEmpty();
  }

  @Test
  void onlyTheOwnersHarvestNodesOnAMatchingChannelCount() {
    farmland(1, 63, 0);
    record = NodeRecord.builder(STRANGER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(4, 1, 4)).build();
    node();
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEmpty();

    nearby.clear();
    record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(4, 1, 4)).channel(DyeColor.RED).build();
    node();
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEmpty();

    channels = ChannelMask.of(DyeColor.RED);
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEqualTo("plant 1,63,0");
  }

  @Test
  void tendingSpendsOneBoneMealFromTheHomeChestAndStartsTheCooldown() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    boneMeal(5);
    GolemTask task = task("tend", 2, 64, 2);

    assertThat(behavior.act(golem(0, List.of()), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(chest[4].amount()).isEqualTo(5);

    assertThat(behavior.act(golem(1, List.of()), task)).isEqualTo(GolemActResult.DONE);

    assertThat(field.age(2, 64, 2)).isEqualTo(4);
    assertThat(chest[4].amount()).isEqualTo(4);
    verify(core).tended(GOLEM, NOW);
    feedback.verify(() -> HarvestFeedback.play(eq(core), any(Loc.class), eq(HarvestFeedback.Kind.TEND)));
  }

  @Test
  void tendingOpensTheHomeChestLidOnceTheBoneMealComesOut() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    boneMeal(5);
    GolemTask task = task("tend", 2, 64, 2);
    GolemContext winding = golem(0, List.of());

    assertThat(behavior.act(winding, task)).isEqualTo(GolemActResult.WORKING);
    verify(winding, never()).visit(any(BlockRef.class));

    GolemContext tending = golem(1, List.of());
    assertThat(behavior.act(tending, task)).isEqualTo(GolemActResult.DONE);
    verify(tending).visit(field.block(CHEST.x(), CHEST.y(), CHEST.z()));
  }

  @Test
  void tendingWithoutBoneMealLeavesTheHomeChestLidAlone() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    boneMeal(0);
    GolemContext golem = golem(1, List.of());

    assertThat(behavior.act(golem, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    verify(golem, never()).visit(any(BlockRef.class));
  }

  @Test
  void theLastBoneMealEmptiesItsSlot() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    boneMeal(1);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.DONE);

    assertThat(chest[4]).isNull();
  }

  @Test
  void tendingWithoutBoneMealOrOnARipeCropFailsAndProtectionBlocksIt() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    field.set(3, 64, 2, Id.minecraft("carrots"), CropField.ageable(7, 7));
    GolemContext denied = golem(1, List.of());
    when(denied.canInteract(any(Loc.class))).thenReturn(false);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);
    boneMeal(5);
    assertThat(behavior.act(golem(1, List.of()), task("tend", 3, 64, 2))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(denied, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(field.age(2, 64, 2)).isEqualTo(2);
    assertThat(chest[4].amount()).isEqualTo(5);
    verify(core, never()).tended(any(), anyLong());
  }

  @Test
  void tendingNeedsPermissionToBuildAtTheCrop() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    boneMeal(5);
    GolemContext denied = golem(1, List.of());
    when(denied.canPlace(any(Loc.class))).thenReturn(false);

    assertThat(behavior.act(denied, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.BLOCKED);

    verify(denied).canPlace(new Loc(field.world, 2, 64, 2));
    assertThat(field.age(2, 64, 2)).isEqualTo(2);
    assertThat(chest[4].amount()).isEqualTo(5);
  }

  @Test
  void plantingTakesOneCarriedSeed() {
    field.set(1, 63, 0, Id.minecraft("farmland"));
    field.set(2, 63, 0, Id.minecraft("farmland"));
    GolemContext seeded = golem(1, seeds(Id.minecraft("wheat_seeds")));
    when(seeded.take(any(), eq(1))).thenReturn(1);
    GolemContext emptied = golem(1, seeds(Id.minecraft("wheat_seeds")));
    when(emptied.take(any(), eq(1))).thenReturn(0);

    assertThat(behavior.act(seeded, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(emptied, task("plant", 2, 63, 0))).isEqualTo(GolemActResult.FAILED);

    assertThat(field.type(1, 64, 0)).isEqualTo(Id.minecraft("wheat"));
    assertThat(field.type(2, 64, 0)).isEqualTo(Id.minecraft("air"));
    feedback.verify(() -> HarvestFeedback.play(eq(core), any(Loc.class), eq(HarvestFeedback.Kind.PLANT)));
  }

  @Test
  void plantingNeedsPermissionToBuildAboveTheFarmland() {
    field.set(1, 63, 0, Id.minecraft("farmland"));
    GolemContext denied = golem(1, seeds(Id.minecraft("wheat_seeds")));
    when(denied.canPlace(any(Loc.class))).thenReturn(false);

    assertThat(behavior.act(denied, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(field.type(1, 64, 0)).isEqualTo(Id.minecraft("air"));
  }

  @Test
  void shrinkingTheWorkAreaStopsWorkOutsideIt() {
    node();
    farmland(3, 63, 3);
    assertThat(seek(golem(1, seeds(Id.minecraft("wheat_seeds"))))).isEqualTo("plant 3,63,3");

    nearby.clear();
    record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build();
    node();
    GolemContext seeded = golem(1, seeds(Id.minecraft("wheat_seeds")));
    when(seeded.take(any(), eq(1))).thenReturn(1);

    assertThat(seek(seeded)).isEmpty();
    assertThat(behavior.act(seeded, task("plant", 3, 63, 3))).isEqualTo(GolemActResult.FAILED);
    assertThat(field.type(3, 64, 3)).isEqualTo(Id.minecraft("air"));
    assertThat(spots.of(KEY)).isEmpty();
  }

  @Test
  void aFailedActDropsItsMark() {
    young(2, 64, 2);
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(7, 7));
    farmland(1, 63, 0);
    boneMeal(5);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    assertThat(spots.of(KEY)).containsExactly(new HarvestSpots.Mark(HarvesterRules.Spot.PLANT, 1, 63, 0));
  }

  @Test
  void aRefusedBoneMealIsRefunded() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    when(field.block(2, 64, 2).applyBoneMeal(any(BlockFace.class))).thenReturn(false);
    boneMeal(5);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    assertThat(chest[4].type()).isEqualTo(Id.minecraft("bone_meal"));
    assertThat(chest[4].amount()).isEqualTo(5);
    verify(core, never()).tended(any(), anyLong());
  }

  @Test
  void aSlotThatNoLongerHoldsBoneMealIsNeverCharged() {
    field.set(2, 64, 2, Id.minecraft("carrots"), CropField.ageable(2, 7));
    boneMeal(5);
    ItemRef wheat = GolemTestItems.stack(Id.minecraft("wheat"), 3);
    InventoryRef swapped = mock(InventoryRef.class);
    when(swapped.contents()).thenAnswer(invocation -> Arrays.asList(chest.clone()));
    when(swapped.item(4)).thenReturn(wheat);
    GolemContext golem = golem(1, List.of());
    when(golem.container(any(BlockRef.class))).thenReturn(Optional.of(swapped));

    assertThat(behavior.act(golem, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    assertThat(wheat.amount()).isEqualTo(3);
    verify(swapped, never()).setItem(anyInt(), any());
    assertThat(field.age(2, 64, 2)).isEqualTo(2);
  }

  @Test
  void theActionIntervalFollowsTheOwnersLevel() {
    level(2);
    assertThat(behavior.actIntervalTicks(golem(0, List.of()), task("plant", 1, 63, 0))).isEqualTo(30);
    level(4);
    assertThat(behavior.actIntervalTicks(golem(0, List.of()), task("plant", 1, 63, 0))).isEqualTo(18);
  }

  @Test
  void plantingUsesLinkedChestStockAndResumesWhenStockReturns() {
    node();
    farmland(1, 63, 0);
    GolemContext golem = golem(1, List.of());
    assertThat(seek(golem)).isEmpty();

    chest[3] = GolemTestItems.stack(Id.minecraft("wheat_seeds"), 2);
    assertThat(seek(golem)).isEqualTo("plant 1,63,0");
    assertThat(behavior.act(golem(0, List.of()), task("plant", 1, 63, 0))).isEqualTo(GolemActResult.WORKING);
    assertThat(chest[3].amount()).isEqualTo(2);
    assertThat(behavior.act(golem, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(chest[3].amount()).isEqualTo(1);
    assertThat(field.type(1, 64, 0)).isEqualTo(Id.minecraft("wheat"));
    verify(golem, never()).take(any(), anyInt());
  }

  @Test
  void plantingPrefersCargoAndDoesNotMoveChestStockIntoCargo() {
    field.set(1, 63, 0, Id.minecraft("farmland"));
    chest[3] = GolemTestItems.stack(Id.minecraft("carrot"), 4);
    GolemContext golem = golem(1, seeds(Id.minecraft("wheat_seeds")));
    when(golem.take(any(), eq(1))).thenReturn(1);

    assertThat(behavior.act(golem, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(1, 64, 0)).isEqualTo(Id.minecraft("wheat"));
    assertThat(chest[3].amount()).isEqualTo(4);
    verify(golem, never()).carry(any());
  }

  @Test
  void plantingWithChestStockRespectsFiltersStemsAndProtection() {
    node();
    farmland(1, 63, 0);
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Id.minecraft("carrot"), 1))).build();
    nearby.clear();
    node();
    chest[3] = GolemTestItems.stack(Id.minecraft("wheat_seeds"), 4);
    assertThat(seek(golem(1, List.of()))).isEmpty();
    chest[3] = GolemTestItems.stack(Id.minecraft("carrot"), 4);
    GolemContext denied = golem(1, List.of());
    when(denied.canPlace(any(Loc.class))).thenReturn(false);
    assertThat(behavior.act(denied, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(chest[3].amount()).isEqualTo(4);

    field.set(2, 64, 0, Id.minecraft("melon_stem"));
    assertThat(behavior.act(golem(1, List.of()), task("plant", 1, 63, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(chest[3].amount()).isEqualTo(4);
  }

  @Test
  void deniedOrUnownedChestStockIsNeverReadOrConsumed() {
    node();
    farmland(1, 63, 0);
    chest[3] = GolemTestItems.stack(Id.minecraft("wheat_seeds"), 4);
    GolemContext golem = golem(1, List.of());
    when(golem.owns(new Loc(field.world, CHEST.x(), CHEST.y(), CHEST.z()))).thenReturn(false);

    assertThat(seek(golem)).isEmpty();
    assertThat(behavior.act(golem, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(chest[3].amount()).isEqualTo(4);
    verify(golem, never()).container(any(BlockRef.class));
  }

  @Test
  void failedPlacementReturnsItsSeedToTheChest() {
    field.set(1, 63, 0, Id.minecraft("farmland"));
    chest[3] = GolemTestItems.stack(Id.minecraft("wheat_seeds"), 1);
    BlockRef spot = field.block(1, 64, 0);
    doThrow(new IllegalStateException("placement refused")).when(spot).setType(Id.minecraft("wheat"), true);

    assertThatThrownBy(() -> behavior.act(golem(1, List.of()), task("plant", 1, 63, 0)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(chest[3].amount()).isEqualTo(1);
  }

  private void node() {
    nearby.add(new NodeIndex.Entry(KEY, record));
  }

  private void farmland(int x, int y, int z) {
    field.set(x, y, z, Id.minecraft("farmland"));
    spots.record(KEY, x, y, z, HarvesterRules.Spot.PLANT);
  }

  private void young(int x, int y, int z) {
    field.set(x, y, z, Id.minecraft("carrots"), CropField.ageable(2, 7));
    spots.record(KEY, x, y, z, HarvesterRules.Spot.TEND);
  }

  private void boneMeal(int amount) {
    chest[4] = amount > 0 ? GolemTestItems.stack(Id.minecraft("bone_meal"), amount) : null;
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(OWNER)).thenReturn(level);
  }

  private String seek(GolemContext golem) {
    return behavior.seek(golem).map(task -> {
      assertThat(task.node()).contains(KEY);
      assertThat(task.nodeType()).contains(NodeType.HARVEST);
      GolemTarget target = task.target();
      return task.type() + " " + target.blockX() + "," + target.blockY() + "," + target.blockZ();
    }).orElse("");
  }

  private GolemContext golem(int actIndex, List<ItemRef> carried) {
    GolemContext golem = mock(GolemContext.class);
    PlayerRef owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    MobRef body = mock(MobRef.class);
    lenient().when(body.location()).thenReturn(new Loc(field.world, 0.5D, 64D, 0.5D));
    GolemState state = mock(GolemState.class);
    lenient().when(state.channels()).thenAnswer(invocation -> channels);
    lenient().when(state.cores()).thenReturn(List.of(GolemCoreType.HARVESTER));
    lenient().when(state.homeChest()).thenAnswer(invocation -> homeChest);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(GOLEM);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.handle()).thenReturn(handle);
    lenient().when(golem.golem()).thenReturn(body);
    lenient().when(golem.world()).thenReturn(field.world);
    lenient().when(golem.node()).thenAnswer(invocation -> Optional.of(record));
    lenient().when(golem.nowMillis()).thenReturn(NOW);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.home()).thenReturn(new Loc(field.world, 0D, 64D, 0D));
    lenient().when(golem.workRadius()).thenReturn(24D);
    lenient().when(golem.inWorkRadius(any(Loc.class))).thenReturn(true);
    lenient().when(golem.owns(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canPlace(any(Loc.class))).thenReturn(true);
    lenient().when(golem.canInteract(any(Loc.class))).thenReturn(true);
    lenient().when(golem.carriedItems()).thenReturn(carried);
    lenient().when(golem.container(any(BlockRef.class))).thenAnswer(invocation -> {
      BlockRef block = invocation.getArgument(0);
      return homeChest != null && block.x() == CHEST.x() && block.y() == CHEST.y() && block.z() == CHEST.z()
          ? Optional.of(inventory()) : Optional.empty();
    });
    return golem;
  }

  private InventoryRef inventory() {
    InventoryRef inventory = mock(InventoryRef.class);
    lenient().when(inventory.contents()).thenAnswer(invocation -> Arrays.asList(chest.clone()));
    lenient().when(inventory.item(anyInt())).thenAnswer(invocation -> chest[invocation.<Integer>getArgument(0)]);
    lenient().doAnswer(invocation -> {
      chest[invocation.<Integer>getArgument(0)] = null;
      return null;
    }).when(inventory).clear(anyInt());
    doAnswer(invocation -> {
      chest[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
      return null;
    }).when(inventory).setItem(anyInt(), any());
    return inventory;
  }

  private static List<ItemRef> seeds(Id type) {
    return List.of(GolemTestItems.stack(type, 3));
  }

  private static GolemTask task(String type, int x, int y, int z) {
    return GolemTask.builder(KEY, NodeType.HARVEST, type, GolemTarget.block(CropField.WORLD, x, y, z)).build();
  }
}
