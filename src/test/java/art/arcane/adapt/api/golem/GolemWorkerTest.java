package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.DepositBehavior;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeStatus;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemWorkerTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final NodeKey COLLECTION = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
  private static final NodeKey DEPOSIT = new NodeKey(WORLD, 0, 64, 6, BlockFace.UP);
  private static final GolemState.Anchor HOME_CHEST = GolemState.Anchor.of(WORLD, -6, 64, 0);

  private final AtomicReference<GolemBrainSettings> settings = new AtomicReference<>(GolemBrainSettings.defaults());
  private final AtomicReference<GolemWorkSettings> work = new AtomicReference<>(GolemWorkSettings.defaults());
  private final RecordingController controller = new RecordingController();
  private final Map<String, Block> blocks = new HashMap<>();
  private final List<NodeBehavior> registered = new ArrayList<>();
  private final List<GolemWorkSpeed.Source> speeds = new ArrayList<>();
  private final List<GolemCapacity.Source> capacities = new ArrayList<>();
  private final Adaptation<?> nodePolicy = mock(Adaptation.class);
  private final Adaptation<?> bindingPolicy = mock(Adaptation.class);
  private final AtomicReference<Location> position = new AtomicReference<>();
  private FakeGolemPlatform platform;
  private World world;
  private GolemTaskBoard board;
  private GolemBrain brain;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    platform.join(OWNER, new GolemRoster());
    platform.bindingPolicy = bindingPolicy;
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    lenient().when(nodePolicy.canAccessChest(any(Player.class), any(Location.class))).thenReturn(true);
    lenient().when(bindingPolicy.canAccessChest(any(Player.class), any(Location.class))).thenReturn(true);
    board = new GolemTaskBoard(WORLD, key -> Optional.ofNullable(platform.nodes.get(key)));
    owner(at(0, 64, 2));
    position.set(at(0, 64, 0));
    brain = gatherer();
  }

  @AfterEach
  void tearDown() {
    for (NodeBehavior behavior : registered) {
      for (NodeType type : NodeType.values()) {
        NodeBehaviors.unregister(type, behavior);
      }
    }
    for (GolemWorkSpeed.Source source : speeds) {
      GolemWorkSpeed.unregister(source);
    }
    for (GolemCapacity.Source source : capacities) {
      GolemCapacity.unregister(source);
    }
    GolemWorkXp.setSink(GolemWorkXp.Sink.NONE);
  }

  @Test
  void aWorkingGolemClaimsTheBestTaskWalksToItAndCompletesIt() {
    List<GolemTask> acted = new ArrayList<>();
    collector((golem, task) -> {
      acted.add(task);
      return NodeBehavior.Result.DONE;
    });
    GolemTask low = pickup(10, 64, 0);
    GolemTask high = pickup(8, 64, 0);
    collectionNode(1);
    board.offer(low, 16);
    NodeKey priority = new NodeKey(WORLD, 8, 64, 2, BlockFace.UP);
    platform.nodes.put(priority, NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).priority(5).build());
    GolemTask preferred = GolemTask.builder(priority, NodeType.COLLECTION, "pickup", GolemTarget.block(WORLD, 12, 64, 0)).build();
    board.offer(preferred, 16);
    board.offer(high, 16);
    brain.start();

    platform.runDelayed();
    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
    assertThat(preferred.heldBy(brain.handle().id(), platform.now)).isTrue();
    assertThat(controller.lastMove()).isEqualTo("12.5,64.5,0.5");

    position.set(at(11, 64, 0));
    pulse(250L);
    assertThat(brain.state()).isEqualTo(GolemBrainState.ACT);
    assertThat(acted).containsExactly(preferred);
    assertThat(preferred.isClosed()).isTrue();
    assertThat(brain.handle().state().stats().tasks()).isEqualTo(1L);
    assertThat(board.status(priority, platform.now)).isEqualTo(NodeStatus.WORKING);
  }

  @Test
  void aGolemThatStopsGettingCloserReleasesItsTaskAndSkipsItForTenSeconds() {
    collector((golem, task) -> NodeBehavior.Result.DONE);
    collectionNode(3);
    GolemTask task = pickup(12, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();
    assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue();
    for (int i = 0; i < 15; i++) {
      pulse(250L);
    }
    assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue();
    pulse(250L);

    assertThat(task.holder(platform.now)).isEmpty();
    assertThat(controller.calls).contains("stop");
    for (int i = 0; i < 39; i++) {
      pulse(250L);
      assertThat(task.holder(platform.now)).isEmpty();
    }
    pulse(250L);
    pulse(1_000L);
    assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue();
  }

  @Test
  void walkingCloserRenewsTheLeasePastItsFirstTwentySeconds() {
    collector((golem, task) -> NodeBehavior.Result.DONE);
    collectionNode(3);
    GolemTask task = pickup(20, 64, 0);
    board.offer(task, 16);
    brain.start();
    platform.runDelayed();
    long claimedAt = platform.now;

    for (int step = 1; step <= 9; step++) {
      position.set(at(step, 64, 0));
      pulse(2_000L);
    }
    long afterFirstLease = claimedAt + GolemWorkSettings.defaults().leaseMillis() + 1_000L;
    GolemTaskBoard.Seeker rival = new GolemTaskBoard.Seeker(UUID.randomUUID(), OWNER, List.of(GolemCoreType.GATHERER), GolemBody.IRON,
        ChannelMask.NONE, 0.5D, 64D, 0.5D, 24D, 0.5D, 64D, 0.5D);

    assertThat(board.claim(rival, ignored -> false, afterFirstLease, 20_000L)).isEmpty();
    assertThat(task.heldBy(brain.handle().id(), afterFirstLease)).isTrue();
  }

  @Test
  void repeatedActsWaitTheWorkIntervalScaledByWorkSpeed() {
    List<Long> actedAt = new ArrayList<>();
    collector((golem, task) -> {
      actedAt.add(platform.now);
      return NodeBehavior.Result.WORKING;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 7; i++) {
      pulse(250L);
    }
    assertThat(actedAt).hasSize(2);
    assertThat(actedAt.get(1) - actedAt.get(0)).isEqualTo(1_000L);

    speed(0.5D);
    actedAt.clear();
    for (int i = 0; i < 8; i++) {
      pulse(250L);
    }
    assertThat(actedAt).hasSize(4);
  }

  @Test
  void blockedActsReleaseTheTaskAndMarkTheNodeBlocked() {
    collector((golem, task) -> NodeBehavior.Result.BLOCKED);
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();

    assertThat(task.holder(platform.now)).isEmpty();
    assertThat(task.isClosed()).isFalse();
    assertThat(board.status(COLLECTION, platform.now)).isEqualTo(NodeStatus.BLOCKED);
    pulse(1_000L);
    assertThat(task.holder(platform.now)).isEmpty();
  }

  @Test
  void failedActsDropTheTask() {
    collector((golem, task) -> NodeBehavior.Result.FAILED);
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();

    assertThat(task.isClosed()).isTrue();
    assertThat(board.openCount(COLLECTION)).isZero();
  }

  @Test
  void aTargetInAnotherRegionIsReleasedNeverActedOn() {
    List<GolemTask> acted = new ArrayList<>();
    collector((golem, task) -> {
      acted.add(task);
      return NodeBehavior.Result.DONE;
    });
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    platform.ownsLocations = false;
    brain.start();

    platform.runDelayed();

    assertThat(acted).isEmpty();
    assertThat(task.holder(platform.now)).isEmpty();
  }

  @Test
  void colossusGolemsTakeNoWork() {
    collector((golem, task) -> NodeBehavior.Result.DONE);
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    brain.handle().setColossus(true);
    brain.start();

    platform.runDelayed();

    assertThat(task.holder(platform.now)).isEmpty();
    assertThat(brain.state()).isNotIn(GolemBrainState.MOVE, GolemBrainState.ACT);
  }

  @Test
  void leavingWorkModeReleasesTheTask() {
    collector((golem, task) -> NodeBehavior.Result.DONE);
    collectionNode(3);
    GolemTask task = pickup(12, 64, 0);
    board.offer(task, 16);
    brain.start();
    platform.runDelayed();

    brain.handle().setMode(GolemMode.FOLLOW);
    pulse(250L);

    assertThat(task.holder(platform.now)).isEmpty();
  }

  @Test
  void sleepingReleasesTheTask() {
    collector((golem, task) -> NodeBehavior.Result.DONE);
    collectionNode(3);
    GolemTask task = pickup(12, 64, 0);
    board.offer(task, 16);
    brain.start();
    platform.runDelayed();

    brain.sleep();

    assertThat(task.holder(platform.now)).isEmpty();
  }

  @Test
  void carryingStopsAtTheGolemsCapacity() {
    List<Optional<ItemStack>> leftovers = new ArrayList<>();
    collector((golem, task) -> {
      leftovers.add(golem.carry(GolemTestItems.stack(Material.WHEAT, 100)));
      return NodeBehavior.Result.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(leftovers).singleElement().satisfies(leftover -> assertThat(leftover).hasValueSatisfying(item -> assertThat(item.getAmount()).isEqualTo(36)));
    assertThat(carried(Material.WHEAT)).isEqualTo(64);
  }

  @Test
  void capacitySourcesRaiseTheCarryLimit() {
    capacity(GolemCapacity.Contribution.bonus(0.5D));
    collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 100));
      assertThat(golem.capacity()).isEqualTo(96);
      return NodeBehavior.Result.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(carried(Material.WHEAT)).isEqualTo(96);
  }

  @Test
  void workXpGoesThroughTheHookWithTheConsumingCore() {
    List<String> paid = new ArrayList<>();
    GolemWorkXp.setSink((handle, core, xp) -> paid.add(core.id() + ":" + xp));
    collector((golem, task) -> {
      golem.workXp(0.5D);
      return NodeBehavior.Result.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(paid).containsExactly("gatherer:0.5");
  }

  @Test
  void carriedItemsGoToAMatchingDepositFirst() {
    deposit(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).build());
    Inventory depositInventory = chest(DEPOSIT.x(), DEPOSIT.y(), DEPOSIT.z(), 27);
    Inventory homeInventory = homeChest(27);
    carry(Material.WHEAT, 20);
    brain.start();

    platform.runDelayed();
    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
    assertThat(controller.lastMove()).isEqualTo("0.5,64.5,6.5");

    position.set(at(0, 64, 5));
    pulse(250L);
    assertThat(controller.calls).contains("interact:0,64,6");
    pulse(1_000L);

    assertThat(GolemTestItems.count(depositInventory, Material.WHEAT)).isEqualTo(20);
    assertThat(GolemTestItems.count(homeInventory, Material.WHEAT)).isZero();
    assertThat(brain.handle().state().carried()).isEmpty();
    assertThat(brain.handle().state().stats().itemsMoved()).isEqualTo(20L);
  }

  @Test
  void itemsADepositRefusesGoToTheHomeChest() {
    deposit(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build());
    Inventory depositInventory = chest(DEPOSIT.x(), DEPOSIT.y(), DEPOSIT.z(), 27);
    Inventory homeInventory = homeChest(27);
    carry(Material.WHEAT, 20);
    brain.start();

    platform.runDelayed();
    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);

    assertThat(GolemTestItems.count(homeInventory, Material.WHEAT)).isEqualTo(20);
    assertThat(GolemTestItems.count(depositInventory, Material.WHEAT)).isZero();
  }

  @Test
  void aDepositWaitingForRedstoneAcceptsNothing() {
    deposit(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).redstone(RedstoneMode.POWERED).build());
    chest(DEPOSIT.x(), DEPOSIT.y(), DEPOSIT.z(), 27);
    Inventory homeInventory = homeChest(27);
    carry(Material.WHEAT, 20);
    brain.start();

    platform.runDelayed();
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);

    assertThat(GolemTestItems.count(homeInventory, Material.WHEAT)).isEqualTo(20);
  }

  @Test
  void depositsOnAnotherChannelAreSkipped() {
    deposit(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).channel(DyeColor.RED).build());
    chest(DEPOSIT.x(), DEPOSIT.y(), DEPOSIT.z(), 27);
    homeChest(27);
    carry(Material.WHEAT, 20);
    brain.start();

    platform.runDelayed();

    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
  }

  @Test
  void aFullGolemWithNowhereToDeliverHoldsItsItemsAndSignalsEveryThreeSeconds() {
    carry(Material.WHEAT, 64);
    brain.start();

    platform.runDelayed();
    assertThat(platform.fullSignals).isEqualTo(1);
    for (int i = 0; i < 11; i++) {
      pulse(250L);
    }
    assertThat(platform.fullSignals).isEqualTo(1);
    pulse(250L);
    assertThat(platform.fullSignals).isEqualTo(2);
    assertThat(carried(Material.WHEAT)).isEqualTo(64);
    assertThat(controller.calls).noneMatch(call -> call.startsWith("interact"));
  }

  @Test
  void aFullGolemTakesNoNewTasks() {
    collector((golem, task) -> NodeBehavior.Result.DONE);
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    carry(Material.WHEAT, 64);
    brain.start();

    platform.runDelayed();

    assertThat(task.holder(platform.now)).isEmpty();
  }

  @Test
  void aGolemThatFillsUpMidTaskReleasesItAndDelivers() {
    collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 64));
      return NodeBehavior.Result.WORKING;
    });
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    homeChest(27);
    brain.start();

    platform.runDelayed();
    assertThat(task.holder(platform.now)).isEmpty();
    pulse(250L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
  }

  @Test
  void anIdleWorkingGolemReportsSeekAndWalksHomeAfterIdling() {
    position.set(at(12, 64, 0));
    brain.start();

    platform.runDelayed();
    assertThat(brain.state()).isEqualTo(GolemBrainState.SEEK);
    for (int i = 0; i < 32; i++) {
      pulse(250L);
    }

    assertThat(brain.state()).isEqualTo(GolemBrainState.RETURN_HOME);
  }

  private GolemBrain gatherer() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    lenient().when(golem.getLocation()).thenAnswer(invocation -> position.get().clone());
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.GATHERER,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    GolemHandle handle = new GolemHandle(golem, state, platform);
    return new GolemBrain(handle, controller, new GolemBrain.Context(platform, settings::get, core -> new IdlePolicy(), work::get, ignored -> board));
  }

  private void pulse(long millis) {
    platform.now += millis;
    platform.runDelayed();
  }

  private void collector(BiFunction<GolemContext, GolemTask, NodeBehavior.Result> act) {
    NodeBehavior behavior = new NodeBehavior() {
      @Override
      public Adaptation<?> policy() {
        return nodePolicy;
      }

      @Override
      public void scan(NodeContext node, ScanBudget budget) {
      }

      @Override
      public Result act(GolemContext golem, GolemTask task) {
        return act.apply(golem, task);
      }
    };
    NodeBehaviors.register(NodeType.COLLECTION, behavior);
    registered.add(behavior);
  }

  private void collectionNode(int priority) {
    platform.nodes.put(COLLECTION, NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).priority(priority).build());
  }

  private void deposit(NodeRecord record) {
    platform.nodes.put(DEPOSIT, record);
    DepositBehavior behavior = new DepositBehavior(nodePolicy);
    NodeBehaviors.register(NodeType.DEPOSIT, behavior);
    registered.add(behavior);
  }

  private static GolemTask pickup(int x, int y, int z) {
    return GolemTask.builder(COLLECTION, NodeType.COLLECTION, "pickup", GolemTarget.block(WORLD, x, y, z)).build();
  }

  private void speed(double multiplier) {
    GolemWorkSpeed.Source source = new GolemWorkSpeed.Source() {
      @Override
      public String id() {
        return "test_speed";
      }

      @Override
      public double intervalMultiplier(GolemHandle handle) {
        return multiplier;
      }
    };
    GolemWorkSpeed.register(source);
    speeds.add(source);
  }

  private void capacity(GolemCapacity.Contribution contribution) {
    GolemCapacity.Source source = new GolemCapacity.Source() {
      @Override
      public String id() {
        return "test_capacity";
      }

      @Override
      public GolemCapacity.Contribution carry(GolemHandle handle) {
        return contribution;
      }
    };
    GolemCapacity.register(source);
    capacities.add(source);
  }

  private void carry(Material type, int amount) {
    brain.handle().setCarriedEncoded(List.of(platform.encodeItem(GolemTestItems.stack(type, amount))));
  }

  private int carried(Material type) {
    int total = 0;
    for (String encoded : brain.handle().state().carried()) {
      ItemStack item = platform.decodeItem(encoded);
      if (item.getType() == type) {
        total += item.getAmount();
      }
    }
    return total;
  }

  private Inventory homeChest(int slots) {
    brain.handle().setHomeChest(HOME_CHEST);
    return chest(HOME_CHEST.x(), HOME_CHEST.y(), HOME_CHEST.z(), slots);
  }

  private Inventory chest(int x, int y, int z, int slots) {
    Inventory inventory = GolemTestItems.inventory(slots);
    Chest chest = mock(Chest.class);
    lenient().when(chest.getInventory()).thenReturn(inventory);
    Block block = block(x, y, z);
    lenient().when(block.getState()).thenReturn(chest);
    lenient().when(block.getType()).thenReturn(Material.CHEST);
    return inventory;
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

  private void owner(Location location) {
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    lenient().when(owner.isOnline()).thenReturn(true);
    lenient().when(owner.isOnGround()).thenReturn(true);
    lenient().when(owner.getLocation()).thenAnswer(invocation -> location.clone());
    lenient().when(owner.getWorld()).thenReturn(world);
    platform.players.put(OWNER, owner);
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }

  private static final class RecordingController implements GolemController {
    private final List<String> calls = new ArrayList<>();

    @Override
    public void attach() {
      calls.add("attach");
    }

    @Override
    public void moveTo(Location destination, double speed) {
      calls.add("move:" + destination.getX() + "," + destination.getY() + "," + destination.getZ());
    }

    @Override
    public void stop() {
      calls.add("stop");
    }

    @Override
    public void setTarget(LivingEntity target) {
      calls.add(target == null ? "target:null" : "target");
    }

    @Override
    public void interact(Block container, int ticks) {
      calls.add("interact:" + container.getX() + "," + container.getY() + "," + container.getZ());
    }

    @Override
    public boolean interacting(Block block) {
      return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public void release() {
      calls.add("release");
    }

    private String lastMove() {
      for (int i = calls.size() - 1; i >= 0; i--) {
        if (calls.get(i).startsWith("move:")) {
          return calls.get(i).substring("move:".length());
        }
      }
      return null;
    }
  }

  private static final class IdlePolicy implements GolemTargetPolicy {
    @Override
    public LivingEntity select(GolemHandle golem, Player owner) {
      return null;
    }

    @Override
    public boolean holds(GolemHandle golem, Player owner, LivingEntity target) {
      return false;
    }
  }
}
