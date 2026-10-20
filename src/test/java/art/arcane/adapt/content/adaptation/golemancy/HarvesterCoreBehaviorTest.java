package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
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

class HarvesterCoreBehaviorTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000022d1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000022d2");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000022d3");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);
  private static final GolemState.Anchor CHEST = GolemState.Anchor.of(CropField.WORLD, 5, 64, 5);
  private static final long NOW = 70_000L;

  private final GolemHarvesterCore.Config config = new GolemHarvesterCore.Config();
  private final CropField field = new CropField();
  private final HarvestSpots spots = new HarvestSpots();
  private final ItemStack[] chest = new ItemStack[27];
  private final List<NodeIndex.Entry> nearby = new ArrayList<>();
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(4, 1, 4)).build();
  private GolemHarvesterCore core;
  private HarvesterCoreBehavior behavior;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<HarvestFeedback> feedback;
  private GolemState.Anchor homeChest = CHEST;
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

    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEqualTo("plant 1,63,0");
    assertThat(seek(golem(1, List.of()))).isEmpty();
    assertThat(seek(golem(1, seeds(Material.MELON_SEEDS)))).isEmpty();
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
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEqualTo("tend 2,64,2");
    level(2);
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEmpty();
  }

  @Test
  void anOwnerWhoSwitchesTendingOrTillingOffGetsNeitherJob() {
    node();
    farmland(1, 63, 0);
    young(2, 64, 2);
    boneMeal(5);

    when(core.tills(OWNER)).thenReturn(false);
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEqualTo("tend 2,64,2");

    when(core.tends(OWNER)).thenReturn(false);
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEmpty();

    when(core.tills(OWNER)).thenReturn(true);
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEqualTo("plant 1,63,0");
  }

  @Test
  void aTendOrPlantJobFailsOnceTheOwnerSwitchesItOff() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    field.set(1, 63, 0, Material.FARMLAND);
    boneMeal(5);
    when(core.tends(OWNER)).thenReturn(false);
    when(core.tills(OWNER)).thenReturn(false);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(golem(1, seeds(Material.WHEAT_SEEDS)), task("plant", 1, 63, 0))).isEqualTo(GolemActResult.FAILED);

    assertThat(chest[4].getAmount()).isEqualTo(5);
    assertThat(field.age(2, 64, 2)).isEqualTo(2);
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.AIR);
  }

  @Test
  void skippedSpotsAndSpotsBeyondTheWorkRadiusArePassedOver() {
    node();
    farmland(1, 63, 0);
    farmland(3, 63, 3);
    GolemContext golem = golem(1, seeds(Material.WHEAT_SEEDS));
    when(golem.skips(any(GolemTask.class))).thenAnswer(invocation -> invocation.<GolemTask>getArgument(0).target().blockX() == 1);

    assertThat(seek(golem)).isEqualTo("plant 3,63,3");

    when(golem.inWorkRadius(any(Location.class))).thenAnswer(invocation -> invocation.<Location>getArgument(0).getBlockX() == 1);
    assertThat(seek(golem)).isEmpty();
  }

  @Test
  void onlyTheOwnersHarvestNodesOnAMatchingChannelCount() {
    farmland(1, 63, 0);
    record = NodeRecord.builder(STRANGER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(4, 1, 4)).build();
    node();
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEmpty();

    nearby.clear();
    record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(4, 1, 4)).channel(DyeColor.RED).build();
    node();
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEmpty();

    channels = ChannelMask.of(DyeColor.RED);
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEqualTo("plant 1,63,0");
  }

  @Test
  void tendingSpendsOneBoneMealFromTheHomeChestAndStartsTheCooldown() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    boneMeal(5);
    GolemTask task = task("tend", 2, 64, 2);

    assertThat(behavior.act(golem(0, List.of()), task)).isEqualTo(GolemActResult.WORKING);
    assertThat(chest[4].getAmount()).isEqualTo(5);

    assertThat(behavior.act(golem(1, List.of()), task)).isEqualTo(GolemActResult.DONE);

    assertThat(field.age(2, 64, 2)).isEqualTo(4);
    assertThat(chest[4].getAmount()).isEqualTo(4);
    verify(core).tended(GOLEM, NOW);
    feedback.verify(() -> HarvestFeedback.play(eq(core), any(Location.class), eq(HarvestFeedback.Kind.TEND)));
  }

  @Test
  void tendingOpensTheHomeChestLidOnceTheBoneMealComesOut() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    boneMeal(5);
    GolemTask task = task("tend", 2, 64, 2);
    GolemContext winding = golem(0, List.of());

    assertThat(behavior.act(winding, task)).isEqualTo(GolemActResult.WORKING);
    verify(winding, never()).visit(any(Block.class));

    GolemContext tending = golem(1, List.of());
    assertThat(behavior.act(tending, task)).isEqualTo(GolemActResult.DONE);
    verify(tending).visit(field.block(CHEST.x(), CHEST.y(), CHEST.z()));
  }

  @Test
  void tendingWithoutBoneMealLeavesTheHomeChestLidAlone() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    boneMeal(0);
    GolemContext golem = golem(1, List.of());

    assertThat(behavior.act(golem, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    verify(golem, never()).visit(any(Block.class));
  }

  @Test
  void theLastBoneMealEmptiesItsSlot() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    boneMeal(1);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.DONE);

    assertThat(chest[4]).isNull();
  }

  @Test
  void tendingWithoutBoneMealOrOnARipeCropFailsAndProtectionBlocksIt() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    field.set(3, 64, 2, Material.CARROTS, CropField.ageable(7, 7));
    GolemContext denied = golem(1, List.of());
    when(denied.canInteract(any(Location.class))).thenReturn(false);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);
    boneMeal(5);
    assertThat(behavior.act(golem(1, List.of()), task("tend", 3, 64, 2))).isEqualTo(GolemActResult.FAILED);
    assertThat(behavior.act(denied, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(field.age(2, 64, 2)).isEqualTo(2);
    assertThat(chest[4].getAmount()).isEqualTo(5);
    verify(core, never()).tended(any(), anyLong());
  }

  @Test
  void tendingNeedsPermissionToBuildAtTheCrop() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    boneMeal(5);
    GolemContext denied = golem(1, List.of());
    when(denied.canPlace(any(Location.class))).thenReturn(false);

    assertThat(behavior.act(denied, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.BLOCKED);

    verify(denied).canPlace(new Location(field.world, 2, 64, 2));
    assertThat(field.age(2, 64, 2)).isEqualTo(2);
    assertThat(chest[4].getAmount()).isEqualTo(5);
  }

  @Test
  void plantingTakesOneCarriedSeed() {
    field.set(1, 63, 0, Material.FARMLAND);
    field.set(2, 63, 0, Material.FARMLAND);
    GolemContext seeded = golem(1, seeds(Material.WHEAT_SEEDS));
    when(seeded.take(any(), eq(1))).thenReturn(1);
    GolemContext emptied = golem(1, seeds(Material.WHEAT_SEEDS));
    when(emptied.take(any(), eq(1))).thenReturn(0);

    assertThat(behavior.act(seeded, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(emptied, task("plant", 2, 63, 0))).isEqualTo(GolemActResult.FAILED);

    assertThat(field.type(1, 64, 0)).isEqualTo(Material.WHEAT);
    assertThat(field.type(2, 64, 0)).isEqualTo(Material.AIR);
    feedback.verify(() -> HarvestFeedback.play(eq(core), any(Location.class), eq(HarvestFeedback.Kind.PLANT)));
  }

  @Test
  void plantingNeedsPermissionToBuildAboveTheFarmland() {
    field.set(1, 63, 0, Material.FARMLAND);
    GolemContext denied = golem(1, seeds(Material.WHEAT_SEEDS));
    when(denied.canPlace(any(Location.class))).thenReturn(false);

    assertThat(behavior.act(denied, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.AIR);
  }

  @Test
  void shrinkingTheWorkAreaStopsWorkOutsideIt() {
    node();
    farmland(3, 63, 3);
    assertThat(seek(golem(1, seeds(Material.WHEAT_SEEDS)))).isEqualTo("plant 3,63,3");

    nearby.clear();
    record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build();
    node();
    GolemContext seeded = golem(1, seeds(Material.WHEAT_SEEDS));
    when(seeded.take(any(), eq(1))).thenReturn(1);

    assertThat(seek(seeded)).isEmpty();
    assertThat(behavior.act(seeded, task("plant", 3, 63, 3))).isEqualTo(GolemActResult.FAILED);
    assertThat(field.type(3, 64, 3)).isEqualTo(Material.AIR);
    assertThat(spots.of(KEY)).isEmpty();
  }

  @Test
  void aFailedActDropsItsMark() {
    young(2, 64, 2);
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(7, 7));
    farmland(1, 63, 0);
    boneMeal(5);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    assertThat(spots.of(KEY)).containsExactly(new HarvestSpots.Mark(HarvesterRules.Spot.PLANT, 1, 63, 0));
  }

  @Test
  void aRefusedBoneMealIsRefunded() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    when(field.block(2, 64, 2).applyBoneMeal(any(BlockFace.class))).thenReturn(false);
    boneMeal(5);

    assertThat(behavior.act(golem(1, List.of()), task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    assertThat(chest[4].getType()).isEqualTo(Material.BONE_MEAL);
    assertThat(chest[4].getAmount()).isEqualTo(5);
    verify(core, never()).tended(any(), anyLong());
  }

  @Test
  void aSlotThatNoLongerHoldsBoneMealIsNeverCharged() {
    field.set(2, 64, 2, Material.CARROTS, CropField.ageable(2, 7));
    boneMeal(5);
    ItemStack wheat = GolemTestItems.stack(Material.WHEAT, 3);
    Inventory swapped = mock(Inventory.class);
    when(swapped.getStorageContents()).thenAnswer(invocation -> chest.clone());
    when(swapped.getItem(4)).thenReturn(wheat);
    GolemContext golem = golem(1, List.of());
    when(golem.container(any(Block.class))).thenReturn(Optional.of(swapped));

    assertThat(behavior.act(golem, task("tend", 2, 64, 2))).isEqualTo(GolemActResult.FAILED);

    assertThat(wheat.getAmount()).isEqualTo(3);
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

    chest[3] = GolemTestItems.stack(Material.WHEAT_SEEDS, 2);
    assertThat(seek(golem)).isEqualTo("plant 1,63,0");
    assertThat(behavior.act(golem(0, List.of()), task("plant", 1, 63, 0))).isEqualTo(GolemActResult.WORKING);
    assertThat(chest[3].getAmount()).isEqualTo(2);
    assertThat(behavior.act(golem, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(chest[3].getAmount()).isEqualTo(1);
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.WHEAT);
    verify(golem, never()).take(any(), anyInt());
  }

  @Test
  void plantingPrefersCargoAndDoesNotMoveChestStockIntoCargo() {
    field.set(1, 63, 0, Material.FARMLAND);
    chest[3] = GolemTestItems.stack(Material.CARROT, 4);
    GolemContext golem = golem(1, seeds(Material.WHEAT_SEEDS));
    when(golem.take(any(), eq(1))).thenReturn(1);

    assertThat(behavior.act(golem, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.DONE);
    assertThat(field.type(1, 64, 0)).isEqualTo(Material.WHEAT);
    assertThat(chest[3].getAmount()).isEqualTo(4);
    verify(golem, never()).carry(any());
  }

  @Test
  void plantingWithChestStockRespectsFiltersStemsAndProtection() {
    node();
    farmland(1, 63, 0);
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build();
    nearby.clear();
    node();
    chest[3] = GolemTestItems.stack(Material.WHEAT_SEEDS, 4);
    assertThat(seek(golem(1, List.of()))).isEmpty();
    chest[3] = GolemTestItems.stack(Material.CARROT, 4);
    GolemContext denied = golem(1, List.of());
    when(denied.canPlace(any(Location.class))).thenReturn(false);
    assertThat(behavior.act(denied, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(chest[3].getAmount()).isEqualTo(4);

    field.set(2, 64, 0, Material.MELON_STEM);
    assertThat(behavior.act(golem(1, List.of()), task("plant", 1, 63, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(chest[3].getAmount()).isEqualTo(4);
  }

  @Test
  void deniedOrUnownedChestStockIsNeverReadOrConsumed() {
    node();
    farmland(1, 63, 0);
    chest[3] = GolemTestItems.stack(Material.WHEAT_SEEDS, 4);
    GolemContext golem = golem(1, List.of());
    when(golem.owns(new Location(field.world, CHEST.x(), CHEST.y(), CHEST.z()))).thenReturn(false);

    assertThat(seek(golem)).isEmpty();
    assertThat(behavior.act(golem, task("plant", 1, 63, 0))).isEqualTo(GolemActResult.FAILED);
    assertThat(chest[3].getAmount()).isEqualTo(4);
    verify(golem, never()).container(any(Block.class));
  }

  @Test
  void failedPlacementReturnsItsSeedToTheChest() {
    field.set(1, 63, 0, Material.FARMLAND);
    chest[3] = GolemTestItems.stack(Material.WHEAT_SEEDS, 1);
    Block spot = field.block(1, 64, 0);
    doThrow(new IllegalStateException("placement refused")).when(spot).setType(Material.WHEAT);

    assertThatThrownBy(() -> behavior.act(golem(1, List.of()), task("plant", 1, 63, 0)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(chest[3].getAmount()).isEqualTo(1);
  }

  private void node() {
    nearby.add(new NodeIndex.Entry(KEY, record));
  }

  private void farmland(int x, int y, int z) {
    field.set(x, y, z, Material.FARMLAND);
    spots.record(KEY, x, y, z, HarvesterRules.Spot.PLANT);
  }

  private void young(int x, int y, int z) {
    field.set(x, y, z, Material.CARROTS, CropField.ageable(2, 7));
    spots.record(KEY, x, y, z, HarvesterRules.Spot.TEND);
  }

  private void boneMeal(int amount) {
    chest[4] = amount > 0 ? GolemTestItems.stack(Material.BONE_MEAL, amount) : null;
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

  private GolemContext golem(int actIndex, List<ItemStack> carried) {
    GolemContext golem = mock(GolemContext.class);
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    Mob body = mock(Mob.class);
    lenient().when(body.getLocation()).thenReturn(new Location(field.world, 0.5D, 64D, 0.5D));
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
    lenient().when(golem.home()).thenReturn(new Location(field.world, 0D, 64D, 0D));
    lenient().when(golem.workRadius()).thenReturn(24D);
    lenient().when(golem.inWorkRadius(any(Location.class))).thenReturn(true);
    lenient().when(golem.owns(any(Location.class))).thenReturn(true);
    lenient().when(golem.canPlace(any(Location.class))).thenReturn(true);
    lenient().when(golem.canInteract(any(Location.class))).thenReturn(true);
    lenient().when(golem.carriedItems()).thenReturn(carried);
    lenient().when(golem.container(any(Block.class))).thenAnswer(invocation -> {
      Block block = invocation.getArgument(0);
      return homeChest != null && block.getX() == CHEST.x() && block.getY() == CHEST.y() && block.getZ() == CHEST.z()
          ? Optional.of(inventory()) : Optional.empty();
    });
    return golem;
  }

  private Inventory inventory() {
    Inventory inventory = mock(Inventory.class);
    lenient().when(inventory.getStorageContents()).thenAnswer(invocation -> chest.clone());
    lenient().when(inventory.getItem(anyInt())).thenAnswer(invocation -> chest[invocation.<Integer>getArgument(0)]);
    doAnswer(invocation -> {
      chest[invocation.<Integer>getArgument(0)] = invocation.getArgument(1);
      return null;
    }).when(inventory).setItem(anyInt(), any());
    return inventory;
  }

  private static List<ItemStack> seeds(Material type) {
    return List.of(GolemTestItems.stack(type, 3));
  }

  private static GolemTask task(String type, int x, int y, int z) {
    return GolemTask.builder(KEY, NodeType.HARVEST, type, GolemTarget.block(CropField.WORLD, x, y, z)).build();
  }
}
