package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemActResult;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemTaskBoard;
import art.arcane.adapt.content.golem.GolemTaskBoards;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.value.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NodeWorkTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final NodeKey KEY = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);

  private final FakeNodePlatform platform = new FakeNodePlatform();
  private final Map<NodeKey, NodeRecord> records = new HashMap<>();
  private final Map<NodeKey, NodeStatus> statuses = new HashMap<>();
  private final Map<String, BlockRef> blocks = new HashMap<>();
  private final List<NodeBehavior> registered = new ArrayList<>();
  private final Adaptation<?> policy = mock(Adaptation.class);
  private WorldRef world;
  private GolemTaskBoards boards;
  private GolemTaskBoard board;
  private NodeWork work;

  @BeforeEach
  void setUp() {
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.blockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    boards = new GolemTaskBoards(key -> Optional.ofNullable(records.get(key)));
    board = boards.board(WORLD);
    work = new NodeWork(platform, boards, statuses::put);
    PlayerRef owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    platform.players.put(OWNER, owner);
    lenient().when(policy.canAccessChest(any(PlayerRef.class), any(Loc.class))).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    for (NodeBehavior behavior : registered) {
      for (NodeType type : NodeType.values()) {
        NodeBehaviors.unregister(type, behavior);
      }
    }
  }

  @Test
  void aScanPassHandsTheBehaviorItsWindowAndTheNodesFreeTaskRoom() {
    NodeRecord record = harvest();
    board.offer(crop(1), 16);
    board.offer(crop(2), 16);
    List<ScanBudget> budgets = new ArrayList<>();
    List<Boolean> posted = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> {
      budgets.add(budget);
      for (int x = 3; x <= 20; x++) {
        posted.add(node.post(node.task("harvest", GolemTarget.block(WORLD, x, 64, 1)).build()));
      }
    });

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(64, 64), 16));

    assertThat(budgets).singleElement().satisfies(budget -> {
      assertThat(budget.start()).isEqualTo(64);
      assertThat(budget.end()).isEqualTo(128);
      assertThat(budget.count()).isEqualTo(64);
    });
    assertThat(posted).filteredOn(Boolean::booleanValue).hasSize(14);
    assertThat(board.openCount(KEY)).isEqualTo(16);
  }

  @Test
  void postedTasksLandOnTheBoardUntilTheNodeIsFull() {
    NodeRecord record = harvest();
    List<Boolean> posted = new ArrayList<>();
    List<Boolean> room = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> {
      for (int x = 1; x <= 5; x++) {
        posted.add(node.post(node.task("harvest", GolemTarget.block(WORLD, x, 64, 1)).build()));
        room.add(budget.hasTaskRoom());
      }
    });

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 3));

    assertThat(posted).containsExactly(true, true, true, false, false);
    assertThat(room).containsExactly(true, true, false, false, false);
    assertThat(board.openCount(KEY)).isEqualTo(3);
  }

  @Test
  void repostingAnOpenTargetKeepsItsTaskAndUsesNoRoom() {
    NodeRecord record = harvest();
    board.offer(crop(1), 16);
    List<Boolean> fresh = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> {
      assertThat(node.post(node.task("harvest", GolemTarget.block(WORLD, 1, 64, 1)).build())).isTrue();
      assertThat(node.tasks()).hasSize(1);
      for (int x = 2; x <= 5; x++) {
        fresh.add(node.post(node.task("harvest", GolemTarget.block(WORLD, x, 64, 1)).build()));
      }
    });

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 4));

    assertThat(fresh).containsExactly(true, true, true, false);
  }

  @Test
  void theNodeLabelStatusFollowsBoardActivity() {
    NodeRecord record = harvest();
    GolemTask task = crop(1);
    board.offer(task, 16);
    behavior(NodeType.HARVEST, (node, budget) -> {
    });
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);

    work.scan(pass);
    assertThat(statuses).containsEntry(KEY, NodeStatus.WAITING);

    board.claim(new GolemTaskBoard.Seeker(GOLEM, OWNER, List.of(GolemCoreType.HARVESTER), GolemBody.IRON, ChannelMask.NONE,
        0.5D, 64D, 0.5D, 24D, 0.5D, 64D, 0.5D), ignored -> false, platform.now, 20_000L);
    work.scan(pass);
    assertThat(statuses).containsEntry(KEY, NodeStatus.WORKING);

    board.release(task, GOLEM);
    board.markBlocked(KEY, platform.now);
    work.scan(pass);
    assertThat(statuses).containsEntry(KEY, NodeStatus.BLOCKED);
  }

  @Test
  void aBehaviorCanSetTheNodeStatusItself() {
    NodeRecord record = harvest();
    behavior(NodeType.HARVEST, (node, budget) -> node.status(NodeStatus.FULL));

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16));

    assertThat(statuses).containsEntry(KEY, NodeStatus.FULL);
  }

  @Test
  void nodesWithoutABehaviorAreLeftAlone() {
    NodeRecord record = harvest();

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16));

    assertThat(statuses).isEmpty();
  }

  @Test
  void aFailingBehaviorDoesNotEscapeTheScan() {
    NodeRecord record = harvest();
    behavior(NodeType.HARVEST, (node, budget) -> {
      throw new IllegalStateException("scan refused");
    });

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16));

    assertThat(statuses).containsEntry(KEY, NodeStatus.IDLE);
  }

  @Test
  void containerReadsAreCachedPerPassAndBothHalvesOfADoubleChestAreOneInventory() {
    NodeRecord record = harvest();
    InventoryRef fromLeft = NodeTestItems.inventory(54);
    InventoryRef fromRight = fromLeft;
    BlockRef left = containerBlock(4, 64, 4, fromLeft);
    BlockRef right = containerBlock(5, 64, 4, fromRight);
    lenient().when(left.containerHalves()).thenReturn(List.of(left, right));
    lenient().when(right.containerHalves()).thenReturn(List.of(left, right));
    List<Optional<InventoryRef>> reads = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> {
      reads.add(node.container(left));
      reads.add(node.container(left));
      reads.add(node.container(right));
    });

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16));

    assertThat(reads).allSatisfy(read -> assertThat(read).containsSame(fromLeft));
    verify(left, times(1)).container();
    verify(policy, times(1)).canAccessChest(any(PlayerRef.class), any(Loc.class));
  }

  @Test
  void containersNeedTheOwnerOnlineChestAccessAndTheRegion() {
    NodeRecord record = harvest();
    BlockRef chest = containerBlock(4, 64, 4, NodeTestItems.inventory(27));
    List<Optional<InventoryRef>> reads = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> reads.add(node.container(chest)));
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);

    platform.ownsEverything = false;
    work.scan(pass);
    platform.ownsEverything = true;
    platform.players.clear();
    work.scan(pass);
    PlayerRef owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    platform.players.put(OWNER, owner);
    lenient().when(policy.canAccessChest(any(PlayerRef.class), any(Loc.class))).thenReturn(false);
    work.scan(pass);
    lenient().when(policy.canAccessChest(any(PlayerRef.class), any(Loc.class))).thenReturn(true);
    work.scan(pass);

    assertThat(reads).hasSize(4);
    assertThat(reads.subList(0, 3)).allSatisfy(read -> assertThat(read).isEmpty());
    assertThat(reads.get(3)).isPresent();
  }

  @Test
  void aDepositScanMarksAFullContainer() {
    InventoryRef inventory = NodeTestItems.inventory(1);
    containerBlock(0, 64, 0, inventory);
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).build();
    records.put(KEY, record);
    DepositBehavior deposit = new DepositBehavior(policy);
    NodeBehaviors.register(NodeType.DEPOSIT, deposit);
    registered.add(deposit);
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);

    work.scan(pass);
    assertThat(statuses).containsEntry(KEY, NodeStatus.IDLE);

    inventory.addItem(NodeTestItems.stack("wheat", 64));
    work.scan(pass);
    assertThat(statuses).containsEntry(KEY, NodeStatus.FULL);
    assertThat(board.openCount(KEY)).isZero();
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class, names = {"DEPOSIT", "INPUT", "FILTER"})
  void aContainerNodeWithoutItsContainerPausesWithdrawsItsTasksAndResumesWhenOneReturns(NodeType type) {
    NodeRecord record = NodeRecord.builder(OWNER, type, BlockFace.UP).build();
    records.put(KEY, record);
    lenient().when(block(0, 64, 0).type()).thenReturn(Id.minecraft("air"));
    GolemTask open = GolemTask.builder(KEY, type, "work", GolemTarget.block(WORLD, 1, 64, 1)).build();
    board.offer(open, 16);
    List<NodeKey> scans = new ArrayList<>();
    behavior(type, (node, budget) -> scans.add(node.key()));
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);

    work.scan(pass);

    assertThat(scans).isEmpty();
    assertThat(statuses).containsEntry(KEY, NodeStatus.NO_CONTAINER);
    assertThat(open.isClosed()).isTrue();
    assertThat(board.openCount(KEY)).isZero();

    containerBlock(0, 64, 0, NodeTestItems.inventory(27));
    work.scan(pass);

    assertThat(scans).containsExactly(KEY);
    assertThat(statuses).containsEntry(KEY, NodeStatus.IDLE);
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class, names = {"GUARD", "COLLECTION", "HARVEST", "LUMBER", "BREAKER", "PASTURE", "FISHING"})
  void anAreaNodeKeepsScanningWithoutItsAnchor(NodeType type) {
    NodeRecord record = NodeRecord.builder(OWNER, type, BlockFace.UP).build();
    records.put(KEY, record);
    lenient().when(block(0, 64, 0).type()).thenReturn(Id.minecraft("air"));
    List<NodeKey> scans = new ArrayList<>();
    behavior(type, (node, budget) -> scans.add(node.key()));

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16));

    assertThat(scans).containsExactly(KEY);
    assertThat(statuses).containsEntry(KEY, NodeStatus.IDLE);
  }

  @Test
  void anInactiveNodeWithdrawsItsOpenTasksAndShowsPaused() {
    NodeRecord record = harvest();
    GolemTask open = crop(1);
    GolemTask leased = crop(2);
    board.offer(open, 16);
    board.offer(leased, 16);
    board.claim(new GolemTaskBoard.Seeker(GOLEM, OWNER, List.of(GolemCoreType.HARVESTER), GolemBody.IRON, ChannelMask.NONE,
        0.5D, 64D, 0.5D, 24D, 2.5D, 64D, 1.5D), ignored -> false, platform.now, 20_000L);
    statuses.put(KEY, NodeStatus.WORKING);

    work.inactive(KEY);

    assertThat(open.isClosed()).isTrue();
    assertThat(leased.isClosed()).isTrue();
    assertThat(board.openCount(KEY)).isZero();
    assertThat(statuses).containsEntry(KEY, NodeStatus.PAUSED);
  }

  @Test
  void aStaleScanCannotPostTasksAfterTheNodeIsEdited() {
    NodeRecord record = harvest();
    AtomicBoolean current = new AtomicBoolean(true);
    behavior(NodeType.HARVEST, (node, budget) -> {
      current.set(false);
      assertThat(node.post(node.task("harvest", GolemTarget.block(WORLD, 1, 64, 1)).build())).isFalse();
    });
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16, new NodeDiscovery(), current::get);

    work.scan(pass);

    assertThat(board.openCount(KEY)).isZero();
    assertThat(statuses).isEmpty();
  }

  @Test
  void invalidatingANodeWithdrawsBothOpenAndClaimedWork() {
    harvest();
    GolemTask task = crop(1);
    board.offer(task, 16);
    board.claim(new GolemTaskBoard.Seeker(GOLEM, OWNER, List.of(GolemCoreType.HARVESTER), GolemBody.IRON, ChannelMask.NONE,
        0.5D, 64D, 0.5D, 24D, 0.5D, 64D, 0.5D), ignored -> false, platform.now, 20_000L);

    work.invalidated(KEY);

    assertThat(task.isClosed()).isTrue();
    assertThat(board.openCount(KEY)).isZero();
  }

  @Test
  void unloadingAWorldDropsItsBoard() {
    board.offer(crop(1), 16);

    work.worldUnloaded(WORLD);

    assertThat(boards.board(WORLD)).isNotSameAs(board);
    assertThat(boards.board(WORLD).openCount(KEY)).isZero();
  }

  @Test
  void areaBlocksOutsideTheRegionAreNotHandedOut() {
    NodeRecord record = harvest();
    List<Optional<BlockRef>> blocks = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> blocks.add(node.block(budget.start())));
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);

    work.scan(pass);
    platform.ownsEverything = false;
    work.scan(pass);

    assertThat(blocks.get(0)).isPresent();
    assertThat(blocks.get(1)).isEmpty();
  }

  @Test
  void areaBlocksInUnloadedChunksAreNotHandedOut() {
    NodeRecord record = harvest();
    List<Optional<BlockRef>> blocks = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> blocks.add(node.block(budget.start())));
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);
    int[] first = NodeArea.position(KEY, record, 0);

    work.scan(pass);
    platform.unloadedChunks.add((first[0] >> 4) + "," + (first[2] >> 4));
    work.scan(pass);

    assertThat(blocks.get(0)).isPresent();
    assertThat(blocks.get(1)).isEmpty();
  }

  private NodeRecord harvest() {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build();
    records.put(KEY, record);
    return record;
  }

  private static GolemTask crop(int x) {
    return GolemTask.builder(KEY, NodeType.HARVEST, "harvest", GolemTarget.block(WORLD, x, 64, 1)).build();
  }

  private void behavior(NodeType type, ScanAction action) {
    NodeBehavior behavior = new NodeBehavior() {
      @Override
      public Adaptation<?> policy() {
        return policy;
      }

      @Override
      public void scan(NodeContext node, ScanBudget budget) {
        action.scan(node, budget);
      }

      @Override
      public GolemActResult act(GolemContext golem, GolemTask task) {
        return GolemActResult.DONE;
      }
    };
    NodeBehaviors.register(type, behavior);
    registered.add(behavior);
  }

  private BlockRef containerBlock(int x, int y, int z, InventoryRef inventory) {
    BlockRef block = block(x, y, z);
    lenient().when(block.container()).thenReturn(inventory);
    lenient().when(block.containerHalves()).thenReturn(List.of(block));
    lenient().when(block.type()).thenReturn(Id.minecraft("chest"));
    return block;
  }

  private BlockRef block(int x, int y, int z) {
    return blocks.computeIfAbsent(x + "," + y + "," + z, ignored -> {
      BlockRef block = mock(BlockRef.class);
      lenient().when(block.world()).thenReturn(world);
      lenient().when(block.x()).thenReturn(x);
      lenient().when(block.y()).thenReturn(y);
      lenient().when(block.z()).thenReturn(z);
      lenient().when(block.location()).thenReturn(new Loc(world, x, y, z));
      return block;
    });
  }

  @FunctionalInterface
  private interface ScanAction {
    void scan(NodeContext node, ScanBudget budget);
  }
}
