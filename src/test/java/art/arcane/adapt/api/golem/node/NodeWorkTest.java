package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
  private final Map<String, Block> blocks = new HashMap<>();
  private final List<NodeBehavior> registered = new ArrayList<>();
  private final Adaptation<?> policy = mock(Adaptation.class);
  private World world;
  private GolemTaskBoard board;
  private NodeWork work;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    board = new GolemTaskBoard(WORLD, key -> Optional.ofNullable(records.get(key)));
    work = new NodeWork(platform, ignored -> board, statuses::put);
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    platform.players.put(OWNER, owner);
    lenient().when(policy.canAccessChest(any(Player.class), any(Location.class))).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    for (NodeBehavior behavior : registered) {
      NodeBehaviors.unregister(NodeType.HARVEST, behavior);
      NodeBehaviors.unregister(NodeType.DEPOSIT, behavior);
    }
  }

  @Test
  void aScanPassHandsTheBehaviorItsWindowAndTheNodesFreeTaskRoom() {
    NodeRecord record = harvest();
    board.offer(crop(1), 16);
    board.offer(crop(2), 16);
    List<ScanBudget> budgets = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> budgets.add(budget));

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(64, 64), 16));

    assertThat(budgets).singleElement().satisfies(budget -> {
      assertThat(budget.start()).isEqualTo(64);
      assertThat(budget.end()).isEqualTo(128);
      assertThat(budget.count()).isEqualTo(64);
      assertThat(budget.taskRoom()).isEqualTo(14);
    });
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
    List<Integer> rooms = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> {
      assertThat(node.post(node.task("harvest", GolemTarget.block(WORLD, 1, 64, 1)).build())).isTrue();
      rooms.add(budget.taskRoom());
      assertThat(node.tasks()).hasSize(1);
    });

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 4));

    assertThat(rooms).containsExactly(3);
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
    assertThat(statuses).containsEntry(KEY, NodeStatus.IDLE);

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
    Inventory leftHalf = mock(Inventory.class);
    lenient().when(leftHalf.getLocation()).thenReturn(new Location(world, 4D, 64D, 4D));
    DoubleChestInventory fromLeft = mock(DoubleChestInventory.class);
    DoubleChestInventory fromRight = mock(DoubleChestInventory.class);
    lenient().when(fromLeft.getLeftSide()).thenReturn(leftHalf);
    lenient().when(fromRight.getLeftSide()).thenReturn(leftHalf);
    Block left = containerBlock(4, 64, 4, fromLeft);
    Block right = containerBlock(5, 64, 4, fromRight);
    List<Optional<Inventory>> reads = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> {
      reads.add(node.container(left));
      reads.add(node.container(left));
      reads.add(node.container(right));
    });

    work.scan(new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16));

    assertThat(reads).allSatisfy(read -> assertThat(read).containsSame(fromLeft));
    verify(left, times(1)).getState();
    verify(policy, times(1)).canAccessChest(any(Player.class), any(Location.class));
  }

  @Test
  void containersNeedTheOwnerOnlineChestAccessAndTheRegion() {
    NodeRecord record = harvest();
    Block chest = containerBlock(4, 64, 4, GolemTestItems.inventory(27));
    List<Optional<Inventory>> reads = new ArrayList<>();
    behavior(NodeType.HARVEST, (node, budget) -> reads.add(node.container(chest)));
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);

    platform.ownsEverything = false;
    work.scan(pass);
    platform.ownsEverything = true;
    platform.players.clear();
    work.scan(pass);
    Player owner = mock(Player.class);
    platform.players.put(OWNER, owner);
    lenient().when(policy.canAccessChest(any(Player.class), any(Location.class))).thenReturn(false);
    work.scan(pass);
    lenient().when(policy.canAccessChest(any(Player.class), any(Location.class))).thenReturn(true);
    work.scan(pass);

    assertThat(reads).hasSize(4);
    assertThat(reads.subList(0, 3)).allSatisfy(read -> assertThat(read).isEmpty());
    assertThat(reads.get(3)).isPresent();
  }

  @Test
  void aDepositScanMarksAFullContainer() {
    Inventory inventory = GolemTestItems.inventory(1);
    containerBlock(0, 64, 0, inventory);
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).build();
    records.put(KEY, record);
    DepositBehavior deposit = new DepositBehavior(policy);
    NodeBehaviors.register(NodeType.DEPOSIT, deposit);
    registered.add(deposit);
    NodeScanPass pass = new NodeScanPass(world, KEY, record, new NodeScanCursor.Window(0, 64), 16);

    work.scan(pass);
    assertThat(statuses).containsEntry(KEY, NodeStatus.IDLE);

    inventory.addItem(GolemTestItems.stack(Material.WHEAT, 64));
    work.scan(pass);
    assertThat(statuses).containsEntry(KEY, NodeStatus.FULL);
    assertThat(board.openCount(KEY)).isZero();
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
      public Result act(GolemContext golem, GolemTask task) {
        return Result.DONE;
      }
    };
    NodeBehaviors.register(type, behavior);
    registered.add(behavior);
  }

  private Block containerBlock(int x, int y, int z, Inventory inventory) {
    Chest chest = mock(Chest.class);
    lenient().when(chest.getInventory()).thenReturn(inventory);
    Block block = block(x, y, z);
    lenient().when(block.getState()).thenReturn(chest);
    lenient().when(block.getType()).thenReturn(Material.CHEST);
    return block;
  }

  private Block block(int x, int y, int z) {
    return blocks.computeIfAbsent(x + "," + y + "," + z, ignored -> {
      Block block = mock(Block.class);
      lenient().when(block.getWorld()).thenReturn(world);
      lenient().when(block.getX()).thenReturn(x);
      lenient().when(block.getY()).thenReturn(y);
      lenient().when(block.getZ()).thenReturn(z);
      lenient().when(block.getLocation()).thenReturn(new Location(world, x, y, z));
      return block;
    });
  }

  @FunctionalInterface
  private interface ScanAction {
    void scan(NodeContext node, ScanBudget budget);
  }
}
