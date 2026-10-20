package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.DepositBehavior;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeStatus;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GolemWorkerTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final NodeKey COLLECTION = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
  private static final NodeKey DEPOSIT = new NodeKey(WORLD, 0, 64, 6, BlockFace.UP);
  private static final GolemState.Anchor HOME_CHEST = GolemState.Anchor.of(WORLD, -6, 64, 0);
  private static final GolemToolNote ROD_NOTE = new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_ROD, null);
  private static final GolemToolNeed ROD = new GolemToolNeed("fishing_rod", item -> item.getType() == Material.FISHING_ROD, item -> 0, ROD_NOTE);
  private static final GolemToolNeed NETHERITE = new GolemToolNeed("netherite", item -> item.getType() == Material.NETHERITE_PICKAXE, item -> 0,
      new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_SILK, null));

  private final AtomicReference<GolemBrainSettings> settings = new AtomicReference<>(GolemBrainSettings.defaults());
  private final AtomicReference<GolemWorkSettings> work = new AtomicReference<>(GolemWorkSettings.defaults());
  private final RecordingController controller = new RecordingController();
  private final Map<String, Block> blocks = new HashMap<>();
  private final List<NodeBehavior> registered = new ArrayList<>();
  private final List<GolemWorkSpeed.Source> speeds = new ArrayList<>();
  private final List<GolemCapacity.Source> capacities = new ArrayList<>();
  private final List<GolemCoreBehavior> cores = new ArrayList<>();
  private final List<GolemRunes.Augments> augments = new ArrayList<>();
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
    GolemWorkStats.setSink(GolemWorkStats.Sink.NONE);
    for (GolemCoreBehavior behavior : cores) {
      GolemCoreBehaviors.unregister(GolemCoreType.GATHERER, behavior);
    }
    for (GolemRunes.Augments registered : augments) {
      GolemRunes.unregister(registered);
    }
  }

  @Test
  void aWorkingGolemClaimsTheBestTaskWalksToItAndCompletesIt() {
    List<GolemTask> acted = new ArrayList<>();
    collector((golem, task) -> {
      acted.add(task);
      return GolemActResult.DONE;
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
    collector((golem, task) -> GolemActResult.DONE);
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
    collector((golem, task) -> GolemActResult.DONE);
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
      return GolemActResult.WORKING;
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
    collector((golem, task) -> GolemActResult.BLOCKED);
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
    collector((golem, task) -> GolemActResult.FAILED);
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
      return GolemActResult.DONE;
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
    collector((golem, task) -> GolemActResult.DONE);
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
    collector((golem, task) -> GolemActResult.DONE);
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
    collector((golem, task) -> GolemActResult.DONE);
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
      return GolemActResult.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(leftovers).singleElement().satisfies(leftover -> assertThat(leftover).hasValueSatisfying(item -> assertThat(item.getAmount()).isEqualTo(36)));
    assertThat(carried(Material.WHEAT)).isEqualTo(64);
  }

  @Test
  void takingFromTheCargoRemovesOnlyMatchingItemsUpToTheAmount() {
    List<Integer> taken = new ArrayList<>();
    collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT_SEEDS, 3));
      golem.carry(GolemTestItems.stack(Material.WHEAT, 5));
      taken.add(golem.take(item -> item.getType() == Material.WHEAT_SEEDS, 2));
      taken.add(golem.take(item -> item.getType() == Material.WHEAT_SEEDS, 5));
      taken.add(golem.take(item -> item.getType() == Material.CARROT, 1));
      taken.add(golem.carried());
      return GolemActResult.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(taken).containsExactly(2, 1, 0, 5);
    assertThat(carried(Material.WHEAT_SEEDS)).isZero();
    assertThat(carried(Material.WHEAT)).isEqualTo(5);
  }

  @Test
  void capacitySourcesRaiseTheCarryLimit() {
    capacity(GolemCapacity.Contribution.bonus(0.5D));
    collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 100));
      assertThat(golem.capacity()).isEqualTo(96);
      return GolemActResult.DONE;
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
      return GolemActResult.DONE;
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
  void aDepositDeliveryHoldsTheLidFromArrivalAndTouchesItAgainWhenTheItemsMove() {
    deposit(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).build());
    chest(DEPOSIT.x(), DEPOSIT.y(), DEPOSIT.z(), 27);
    homeChest(27);
    carry(Material.WHEAT, 20);
    brain.start();
    platform.runDelayed();

    position.set(at(0, 64, 5));
    pulse(250L);
    assertThat(platform.lidVisits).containsExactly("0,64,6 35");

    pulse(1_000L);
    assertThat(platform.lidVisits).containsExactly("0,64,6 35", "0,64,6 15");
  }

  @Test
  void aHomeChestDeliveryHoldsTheLidFromArrivalAndTouchesItAgainWhenTheItemsMove() {
    Inventory homeInventory = homeChest(27);
    carry(Material.WHEAT, 20);
    brain.start();
    platform.runDelayed();

    position.set(at(-5, 64, 0));
    pulse(250L);
    assertThat(platform.lidVisits).containsExactly("-6,64,0 35");

    pulse(1_000L);
    assertThat(platform.lidVisits).containsExactly("-6,64,0 35", "-6,64,0 15");
    assertThat(GolemTestItems.count(homeInventory, Material.WHEAT)).isEqualTo(20);
  }

  @Test
  void aSlowerGolemHoldsTheLidThroughItsLongerInteraction() {
    speed(2D);
    homeChest(27);
    carry(Material.WHEAT, 20);
    brain.start();
    platform.runDelayed();

    position.set(at(-5, 64, 0));
    pulse(250L);

    assertThat(platform.lidVisits).containsExactly("-6,64,0 55");
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
    collector((golem, task) -> GolemActResult.DONE);
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
      return GolemActResult.WORKING;
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

  @Test
  void anIdleWorkingGolemAtHomeIssuesNoMovement() {
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 120; i++) {
      pulse(250L);
    }

    assertThat(brain.state()).isEqualTo(GolemBrainState.SEEK);
    assertThat(controller.calls).noneMatch(call -> call.startsWith("move:"));
  }

  @Test
  void anIdleWorkingGolemWalksHomeOnceThenStandsUntilAJobAppears() {
    collector((golem, task) -> GolemActResult.DONE);
    collectionNode(3);
    position.set(at(12, 64, 0));
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 32; i++) {
      pulse(250L);
    }
    assertThat(brain.state()).isEqualTo(GolemBrainState.RETURN_HOME);
    assertThat(controller.lastMove()).isEqualTo("0.5,64.0,0.5");

    position.set(at(0, 64, 1));
    pulse(250L);
    assertThat(controller.calls).endsWith("stop");
    controller.calls.clear();
    for (int i = 0; i < 120; i++) {
      pulse(250L);
    }
    assertThat(controller.calls).noneMatch(call -> call.startsWith("move:"));

    board.offer(pickup(6, 64, 0), 16);
    pulse(1_000L);
    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
    assertThat(controller.lastMove()).isEqualTo("6.5,64.5,0.5");
  }

  @Test
  void arrivingResetsTheStuckWatchSoDriftAfterALongJobIsNotStuck() {
    collector((golem, task) -> GolemActResult.WORKING);
    collectionNode(3);
    GolemTask task = pickup(3, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();
    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
    position.set(at(2, 64, 0));
    pulse(250L);
    assertThat(brain.state()).isEqualTo(GolemBrainState.ACT);
    for (int i = 0; i < 40; i++) {
      pulse(250L);
    }
    position.set(new Location(world, 0.7D, 64D, 0.5D));
    pulse(250L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
    assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue();
  }

  @Test
  void aLongActIntervalKeepsTheClaimBetweenActs() {
    List<Long> actedAt = new ArrayList<>();
    collector((golem, task) -> {
      actedAt.add(platform.now);
      return GolemActResult.WORKING;
    }).intervalTicks = 1_200;
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 59; i++) {
      pulse(1_000L);
    }
    GolemTaskBoard.Seeker rival = new GolemTaskBoard.Seeker(UUID.randomUUID(), OWNER, List.of(GolemCoreType.GATHERER), GolemBody.IRON,
        ChannelMask.NONE, 0.5D, 64D, 0.5D, 24D, 0.5D, 64D, 0.5D);
    assertThat(actedAt).hasSize(1);
    assertThat(board.claim(rival, ignored -> false, platform.now, 20_000L)).isEmpty();

    pulse(1_000L);
    assertThat(actedAt).hasSize(2);
    assertThat(actedAt.get(1) - actedAt.get(0)).isEqualTo(60_000L);
  }

  @Test
  void coresActOnArrivalAndCountTheirActsInTheActIndex() {
    List<String> acts = new ArrayList<>();
    collector((golem, task) -> {
      acts.add(golem.actIndex() + "@" + golem.nowMillis());
      return golem.actIndex() == 0 ? GolemActResult.WORKING : GolemActResult.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    long arrival = platform.now;
    for (int i = 0; i < 4; i++) {
      pulse(250L);
    }

    assertThat(acts).containsExactly("0@" + arrival, "1@" + (arrival + 1_000L));
    assertThat(brain.handle().state().stats().tasks()).isEqualTo(1L);
  }

  @Test
  void aNodeSwitchedOffByRedstoneHasItsTaskWithdrawnAtAct() {
    List<GolemTask> acted = new ArrayList<>();
    collector((golem, task) -> {
      acted.add(task);
      return GolemActResult.DONE;
    });
    platform.nodes.put(COLLECTION, NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).redstone(RedstoneMode.POWERED).build());
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();

    assertThat(acted).isEmpty();
    assertThat(task.isClosed()).isTrue();
  }

  @Test
  void theLastNodesRouteHookIsConsultedBeforeTheDefaultRoute() {
    Inventory stash = chest(0, 64, -5, 27);
    Inventory homeInventory = homeChest(27);
    List<String> acted = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      acted.add(task.type());
      if (task.type().equals("pickup")) {
        golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
        return GolemActResult.DONE;
      }
      golem.container(world.getBlockAt(0, 64, -5)).ifPresent(inventory -> golem.deliver(inventory, item -> true));
      return GolemActResult.DONE;
    });
    behavior.route = (golem, carried) -> Optional.of(GolemTask.builder(COLLECTION, NodeType.COLLECTION, "stash",
        GolemTarget.block(WORLD, 0, 64, -5)).build());
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
    assertThat(controller.lastMove()).isEqualTo("0.5,64.5,-4.5");
    position.set(at(0, 64, -4));
    pulse(250L);

    assertThat(acted).containsExactly("pickup", "stash");
    assertThat(GolemTestItems.count(stash, Material.WHEAT)).isEqualTo(10);
    assertThat(GolemTestItems.count(homeInventory, Material.WHEAT)).isZero();
    assertThat(brain.handle().state().stats().tasks()).isEqualTo(1L);
  }

  @Test
  void aGolemLoadedByANodeThatDeliversFirstRoutesBeforeClaimingMoreWork() {
    Inventory stash = chest(0, 64, -5, 27);
    List<String> acted = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      acted.add(task.type() + " " + task.target().blockX());
      if (task.type().equals("pickup")) {
        golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
      } else {
        golem.container(world.getBlockAt(0, 64, -5)).ifPresent(inventory -> golem.deliver(inventory, item -> true));
      }
      return GolemActResult.DONE;
    });
    behavior.deliversFirst = true;
    behavior.route = (golem, carried) -> Optional.of(GolemTask.builder(COLLECTION, NodeType.COLLECTION, "stash",
        GolemTarget.block(WORLD, 0, 64, -5)).build());
    collectionNode(3);
    GolemTask second = pickup(2, 64, 0);
    board.offer(pickup(1, 64, 0), 16);
    board.offer(second, 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);

    assertThat(acted).containsExactly("pickup 1");
    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
    assertThat(second.holder(platform.now)).isEmpty();

    position.set(at(0, 64, -4));
    pulse(250L);
    position.set(at(0, 64, 0));
    pulse(1_000L);

    assertThat(acted).containsExactly("pickup 1", "stash 0", "pickup 2");
    assertThat(GolemTestItems.count(stash, Material.WHEAT)).isEqualTo(10);
  }

  @Test
  void aNodeThatDeliversFirstLetsTheGolemClaimAgainOnceItsRouteFindsNowhereToGo() {
    List<String> acted = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      acted.add(task.type() + " " + task.target().blockX());
      golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
      return GolemActResult.DONE;
    });
    behavior.deliversFirst = true;
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    board.offer(pickup(2, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    assertThat(acted).containsExactly("pickup 1");

    pulse(1_000L);
    assertThat(acted).containsExactly("pickup 1", "pickup 2");
  }

  @Test
  void aFinishedRouteStopRoutesTheRestOfTheCargoAtOnce() {
    chest(0, 64, -5, 27);
    chest(0, 64, 5, 27);
    ScriptedBehavior behavior = collector((golem, task) -> {
      if (task.type().equals("pickup")) {
        golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
        golem.carry(GolemTestItems.stack(Material.CARROT, 10));
        return GolemActResult.DONE;
      }
      Material kept = task.target().blockZ() < 0 ? Material.WHEAT : Material.CARROT;
      golem.container(world.getBlockAt(0, 64, task.target().blockZ())).ifPresent(inventory -> golem.deliver(inventory, item -> item.getType() == kept));
      return GolemActResult.DONE;
    });
    behavior.route = (golem, carried) -> {
      boolean wheat = carried.stream().anyMatch(item -> item.getType() == Material.WHEAT);
      return Optional.of(GolemTask.builder(COLLECTION, NodeType.COLLECTION, "stash", GolemTarget.block(WORLD, 0, 64, wheat ? -5 : 5)).build());
    };
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    position.set(at(0, 64, -4));
    pulse(250L);
    assertThat(carried(Material.WHEAT)).isZero();

    pulse(250L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
    assertThat(controller.lastMove()).isEqualTo("0.5,64.5,5.5");
  }

  @Test
  void aFailedRouteStopKeepsTheRoutingNodeSoTheNextRouteTriesAnotherStop() {
    chest(0, 64, -5, 27);
    Inventory second = chest(0, 64, 5, 27);
    Inventory homeInventory = homeChest(27);
    ScriptedBehavior behavior = collector((golem, task) -> {
      if (task.type().equals("pickup")) {
        golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
        return GolemActResult.DONE;
      }
      if (task.target().blockZ() < 0) {
        return GolemActResult.FAILED;
      }
      golem.container(world.getBlockAt(0, 64, 5)).ifPresent(inventory -> golem.deliver(inventory, item -> true));
      return GolemActResult.DONE;
    });
    behavior.route = (golem, carried) -> {
      GolemTask first = stash(-5);
      return Optional.of(golem.skips(first) ? stash(5) : first);
    };
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    position.set(at(0, 64, -4));
    pulse(250L);
    pulse(3_000L);

    assertThat(controller.lastMove()).isEqualTo("0.5,64.5,5.5");
    position.set(at(0, 64, 4));
    pulse(250L);
    assertThat(GolemTestItems.count(second, Material.WHEAT)).isEqualTo(10);
    assertThat(GolemTestItems.count(homeInventory, Material.WHEAT)).isZero();
  }

  @Test
  void aNodeThatDeliversFirstReleasesAFailedJobAndBacksOffLongerEachTimeUntilAJobIsDone() {
    List<Long> acts = new ArrayList<>();
    List<GolemActResult> results = new ArrayList<>(List.of(GolemActResult.FAILED, GolemActResult.FAILED, GolemActResult.FAILED,
        GolemActResult.FAILED, GolemActResult.FAILED, GolemActResult.DONE, GolemActResult.FAILED, GolemActResult.FAILED));
    ScriptedBehavior behavior = collector((golem, task) -> {
      acts.add(platform.now);
      return results.removeFirst();
    });
    behavior.deliversFirst = true;
    collectionNode(3);
    GolemTask pickup = pickup(1, 64, 0);
    board.offer(pickup, 16);
    brain.start();

    platform.runDelayed();
    assertThat(pickup.isClosed()).isFalse();
    assertThat(pickup.holder(platform.now)).isEmpty();
    for (int i = 0; i < 191; i++) {
      pulse(1_000L);
    }
    assertThat(acts).hasSize(6);
    board.offer(pickup(1, 64, 0), 16);
    for (int i = 0; i < 12; i++) {
      pulse(1_000L);
    }

    List<Long> gaps = new ArrayList<>();
    for (int i = 1; i < acts.size(); i++) {
      gaps.add(acts.get(i) - acts.get(i - 1));
    }
    assertThat(gaps.subList(0, 5)).containsExactly(10_000L, 20_000L, 40_000L, 60_000L, 60_000L);
    assertThat(acts).hasSize(8);
    assertThat(acts.get(7) - acts.get(6)).isEqualTo(10_000L);
  }

  @Test
  void aLoadedGolemWhoseDeliverFirstClaimFailsStillRoutesThroughThatNode() {
    chest(0, 64, -5, 27);
    List<Integer> asked = new ArrayList<>();
    AtomicReference<GolemTask> stop = new AtomicReference<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      if (task.target().blockX() == 2) {
        return GolemActResult.FAILED;
      }
      golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
      return GolemActResult.DONE;
    });
    behavior.deliversFirst = true;
    behavior.route = (golem, carried) -> {
      asked.add(carried.size());
      return Optional.ofNullable(stop.get());
    };
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    GolemTask second = pickup(2, 64, 0);
    board.offer(second, 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    assertThat(asked).hasSize(1);
    pulse(1_000L);
    assertThat(second.isClosed()).isFalse();
    assertThat(carried(Material.WHEAT)).isEqualTo(10);

    stop.set(stash(-5));
    pulse(3_000L);

    assertThat(asked).hasSize(2);
    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
    assertThat(controller.lastMove()).isEqualTo("0.5,64.5,-4.5");
  }

  @Test
  void otherNodesStillWithdrawAFailedBoardJobWithoutBackingOff() {
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.FAILED);
    collectionNode(3);
    GolemTask pickup = pickup(1, 64, 0);
    board.offer(pickup, 16);
    brain.start();

    platform.runDelayed();

    assertThat(pickup.isClosed()).isTrue();
    assertThat(behavior.deliversFirst).isFalse();
  }

  @Test
  void anEmptyRouteFallsBackToTheDefaultRoute() {
    homeChest(27);
    ScriptedBehavior behavior = collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
      return GolemActResult.DONE;
    });
    List<Integer> asked = new ArrayList<>();
    behavior.route = (golem, carried) -> {
      asked.add(carried.size());
      return Optional.empty();
    };
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);

    assertThat(asked).containsExactly(1);
    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
  }

  @Test
  void aCoreSourceIsAskedForWorkWhenTheBoardHasNone() {
    List<String> seeks = new ArrayList<>();
    List<String> acts = new ArrayList<>();
    List<String> stats = new ArrayList<>();
    GolemWorkStats.setSink(new RecordingStats(stats));
    coreSeeker(golem -> {
      seeks.add(golem.core().id());
      return Optional.of(GolemTask.builder("pickup", GolemTarget.block(WORLD, 2, 64, 0)).build());
    }, (golem, task) -> {
      acts.add(task.type() + ":" + golem.core().id());
      return GolemActResult.DONE;
    });
    brain.start();

    platform.runDelayed();

    assertThat(seeks).containsExactly("gatherer");
    assertThat(acts).containsExactly("pickup:gatherer");
    assertThat(brain.handle().state().stats().tasks()).isEqualTo(1L);
    assertThat(stats).containsExactly("completed");
  }

  @Test
  void theBoardIsAskedBeforeTheCoreSource() {
    List<String> seeks = new ArrayList<>();
    coreSeeker(golem -> {
      seeks.add("core");
      return Optional.empty();
    }, (golem, task) -> GolemActResult.DONE);
    collector((golem, task) -> GolemActResult.WORKING);
    collectionNode(3);
    board.offer(pickup(12, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(seeks).isEmpty();
    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
  }

  @Test
  void finishedJobsAndDeliveredItemsReachTheStatSink() {
    List<String> stats = new ArrayList<>();
    GolemWorkStats.setSink(new RecordingStats(stats));
    collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 20));
      return GolemActResult.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    homeChest(27);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);

    assertThat(stats).containsExactly("completed", "moved:20");
  }

  @Test
  void itemsPutBackFromARouteJobReachNeitherTheStatSinkNorTheGolemsMovedCount() {
    List<String> stats = new ArrayList<>();
    GolemWorkStats.setSink(new RecordingStats(stats));
    Inventory input = chest(0, 64, -5, 27);
    ScriptedBehavior behavior = collector((golem, task) -> {
      if (task.type().equals("pickup")) {
        golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
        return GolemActResult.DONE;
      }
      golem.putBack(golem.container(world.getBlockAt(0, 64, -5)).orElseThrow(), ItemStack::getAmount);
      return GolemActResult.DONE;
    });
    behavior.route = (golem, carried) -> Optional.of(GolemTask.builder("return", GolemTarget.block(WORLD, 0, 64, -5)).build());
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    position.set(at(0, 64, -4));
    pulse(250L);

    assertThat(GolemTestItems.count(input, Material.WHEAT)).isEqualTo(10);
    assertThat(stats).containsExactly("completed");
    assertThat(brain.handle().state().stats().itemsMoved()).isZero();
    assertThat(brain.handle().state().stats().tasks()).isEqualTo(1L);
  }

  @Test
  void aTrappedGolemRestsAfterRepeatedStuckReleases() {
    collector((golem, task) -> GolemActResult.DONE);
    collectionNode(3);
    List<GolemTask> tasks = List.of(pickup(12, 64, 0), pickup(12, 64, 4), pickup(12, 64, -4), pickup(-12, 64, 0));
    for (GolemTask task : tasks) {
      board.offer(task, 16);
    }
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 48; i++) {
      pulse(250L);
    }
    long pausedAt = platform.now;
    for (int i = 0; i < 59; i++) {
      pulse(250L);
      assertThat(tasks).allSatisfy(task -> assertThat(task.holder(platform.now)).isEmpty());
    }
    pulse(250L);

    assertThat(platform.now - pausedAt).isEqualTo(15_000L);
    assertThat(tasks).anySatisfy(task -> assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue());
  }

  @Test
  void aFailedInsertNeverDuplicatesItems() {
    brain.handle().setHomeChest(HOME_CHEST);
    List<Material> inserted = new ArrayList<>();
    Inventory throwing = mock(Inventory.class);
    lenient().when(throwing.getStorageContents()).thenReturn(new ItemStack[27]);
    lenient().when(throwing.getMaxStackSize()).thenReturn(64);
    lenient().when(throwing.addItem(any(ItemStack[].class))).thenAnswer(invocation -> {
      ItemStack item = invocation.getArgument(0);
      if (item.getType() == Material.CARROT) {
        throw new IllegalStateException("inventory refused");
      }
      inserted.add(item.getType());
      return new HashMap<Integer, ItemStack>();
    });
    Chest chest = mock(Chest.class);
    lenient().when(chest.getInventory()).thenReturn(throwing);
    Block block = block(HOME_CHEST.x(), HOME_CHEST.y(), HOME_CHEST.z());
    lenient().when(block.getState(false)).thenReturn(chest);
    brain.handle().setCarriedEncoded(List.of(platform.encodeItem(GolemTestItems.stack(Material.WHEAT, 10)),
        platform.encodeItem(GolemTestItems.stack(Material.CARROT, 10)), platform.encodeItem(GolemTestItems.stack(Material.POTATO, 10))));
    brain.start();

    platform.runDelayed();
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);

    assertThat(inserted).containsExactly(Material.WHEAT);
    assertThat(carried(Material.WHEAT)).isZero();
    assertThat(carried(Material.POTATO)).isEqualTo(10);
  }

  @Test
  void entityTargetsResolveThroughTheRegionCheckedLookup() {
    Zombie zombie = mock(Zombie.class);
    UUID zombieId = UUID.randomUUID();
    lenient().when(zombie.getUniqueId()).thenReturn(zombieId);
    lenient().when(zombie.getWorld()).thenReturn(world);
    lenient().when(zombie.getLocation()).thenReturn(at(1, 64, 0));
    platform.entities.put(zombieId, zombie);
    List<Optional<Entity>> seen = new ArrayList<>();
    collector((golem, task) -> {
      seen.add(golem.targetEntity());
      return GolemActResult.DONE;
    });
    collectionNode(3);
    board.offer(GolemTask.builder(COLLECTION, NodeType.COLLECTION, "pickup", GolemTarget.entity(zombie)).build(), 16);
    brain.start();

    platform.runDelayed();

    assertThat(seen).singleElement().satisfies(entity -> assertThat(entity).containsSame(zombie));
  }

  @Test
  void capacityIsComputedOncePerPulse() {
    int[] calls = new int[1];
    GolemCapacity.Source counting = new GolemCapacity.Source() {
      @Override
      public String id() {
        return "test_capacity";
      }

      @Override
      public GolemCapacity.Contribution carry(GolemHandle handle) {
        calls[0]++;
        return GolemCapacity.Contribution.NONE;
      }
    };
    GolemCapacity.register(counting);
    capacities.add(counting);
    collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 4));
      golem.capacity();
      golem.room();
      return GolemActResult.WORKING;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(calls[0]).isEqualTo(1);
  }

  @Test
  void aCoreWhoseSeekKeepsReturningAFailedTargetBacksItOffForTenSeconds() {
    List<Long> acts = new ArrayList<>();
    coreSeeker(golem -> Optional.of(GolemTask.builder("pickup", GolemTarget.block(WORLD, 1, 64, 0)).build()), (golem, task) -> {
      acts.add(platform.now);
      return GolemActResult.FAILED;
    });
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 9; i++) {
      pulse(1_000L);
    }
    assertThat(acts).hasSize(1);
    pulse(1_000L);

    assertThat(acts).hasSize(2);
    assertThat(acts.get(1) - acts.get(0)).isEqualTo(10_000L);
  }

  @Test
  void deliveringKeepsUntriedItemsWhenTheFilterThrows() {
    Inventory stash = chest(0, 64, -2, 27);
    brain.handle().setCarriedEncoded(List.of(platform.encodeItem(GolemTestItems.stack(Material.WHEAT, 10)),
        platform.encodeItem(GolemTestItems.stack(Material.CARROT, 10)), platform.encodeItem(GolemTestItems.stack(Material.POTATO, 10))));
    collector((golem, task) -> {
      Inventory inventory = golem.container(world.getBlockAt(0, 64, -2)).orElseThrow();
      assertThatThrownBy(() -> golem.deliver(inventory, item -> {
        if (item.getType() == Material.CARROT) {
          throw new IllegalStateException("filter refused");
        }
        return true;
      })).isInstanceOf(IllegalStateException.class);
      return GolemActResult.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(GolemTestItems.count(stash, Material.WHEAT)).isEqualTo(10);
    assertThat(carried(Material.WHEAT)).isZero();
    assertThat(carried(Material.CARROT)).isEqualTo(10);
    assertThat(carried(Material.POTATO)).isEqualTo(10);
  }

  @Test
  void deliveringUpToAnAmountInsertsOnlyThatManyOfEachStackAndKeepsTheRest() {
    Inventory stash = chest(0, 64, -2, 27);
    brain.handle().setCarriedEncoded(List.of(platform.encodeItem(GolemTestItems.stack(Material.WHEAT, 10)),
        platform.encodeItem(GolemTestItems.stack(Material.CARROT, 10)), platform.encodeItem(GolemTestItems.stack(Material.POTATO, 10))));
    List<Integer> moved = new ArrayList<>();
    collector((golem, task) -> {
      Inventory inventory = golem.container(world.getBlockAt(0, 64, -2)).orElseThrow();
      moved.add(golem.deliverUpTo(inventory, item -> switch (item.getType()) {
        case WHEAT -> 4;
        case CARROT -> 25;
        default -> 0;
      }));
      return GolemActResult.DONE;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();

    assertThat(moved).containsExactly(14);
    assertThat(GolemTestItems.count(stash, Material.WHEAT)).isEqualTo(4);
    assertThat(GolemTestItems.count(stash, Material.CARROT)).isEqualTo(10);
    assertThat(GolemTestItems.count(stash, Material.POTATO)).isZero();
    assertThat(carried(Material.WHEAT)).isEqualTo(6);
    assertThat(carried(Material.CARROT)).isZero();
    assertThat(carried(Material.POTATO)).isEqualTo(10);
  }

  @Test
  void aNodelessRouteJobActsWithTheRoutingNodesRecord() {
    chest(0, 64, -5, 27);
    List<Optional<NodeRecord>> routedNodes = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      if (task.type().equals("pickup")) {
        golem.carry(GolemTestItems.stack(Material.WHEAT, 10));
        return GolemActResult.DONE;
      }
      routedNodes.add(golem.node());
      return GolemActResult.DONE;
    });
    behavior.route = (golem, carried) -> Optional.of(GolemTask.builder("stash", GolemTarget.block(WORLD, 0, 64, -5)).build());
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    position.set(at(0, 64, -4));
    pulse(250L);

    assertThat(routedNodes).singleElement().satisfies(node -> assertThat(node).containsSame(platform.nodes.get(COLLECTION)));
  }

  @Test
  void coreJobPickupsRouteByTheDefaultRouteNotByAnEarlierBoardNode() {
    homeChest(27);
    List<String> routed = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 5));
      return GolemActResult.DONE;
    });
    behavior.route = (golem, carried) -> {
      routed.add("node");
      return Optional.empty();
    };
    int[] seeks = new int[1];
    coreSeeker(golem -> seeks[0]++ == 0 ? Optional.of(GolemTask.builder("gather", GolemTarget.block(WORLD, 1, 64, 1)).build()) : Optional.empty(),
        (golem, task) -> {
          golem.carry(GolemTestItems.stack(Material.CARROT, 5));
          return GolemActResult.DONE;
        });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    assertThat(carried(Material.CARROT)).isEqualTo(5);
    pulse(1_000L);

    assertThat(routed).isEmpty();
    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
  }

  @Test
  void aJobThatClosesForgetsItsNodeForRouting() {
    homeChest(27);
    List<String> routed = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      if (golem.actIndex() == 0) {
        golem.carry(GolemTestItems.stack(Material.WHEAT, 5));
        return GolemActResult.WORKING;
      }
      return GolemActResult.FAILED;
    });
    behavior.route = (golem, carried) -> {
      routed.add("node");
      return Optional.empty();
    };
    collectionNode(3);
    GolemTask task = pickup(1, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);
    assertThat(task.isClosed()).isTrue();
    pulse(1_000L);

    assertThat(routed).isEmpty();
    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
  }

  @Test
  void walkingBackHomeForgetsTheLastWorkedNode() {
    List<Long> routed = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      golem.carry(GolemTestItems.stack(Material.WHEAT, 5));
      return GolemActResult.DONE;
    });
    behavior.route = (golem, carried) -> {
      routed.add(platform.now);
      return Optional.empty();
    };
    collectionNode(3);
    position.set(at(12, 64, 0));
    board.offer(pickup(13, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 40; i++) {
      pulse(250L);
    }
    assertThat(brain.state()).isEqualTo(GolemBrainState.RETURN_HOME);
    assertThat(routed).isNotEmpty();
    int beforeReturn = routed.size();
    for (int i = 0; i < 24; i++) {
      pulse(250L);
    }

    assertThat(brain.state()).isEqualTo(GolemBrainState.RETURN_HOME);
    assertThat(routed).hasSize(beforeReturn);
  }

  @Test
  void aCoreJobIsReservedBeforeWalkingAndReleasedWhenItEnds() {
    List<String> acts = new ArrayList<>();
    coreSeeker(golem -> Optional.of(gather(8)), (golem, task) -> {
      acts.add(task.type());
      return GolemActResult.DONE;
    });
    UUID rival = UUID.randomUUID();
    brain.start();

    platform.runDelayed();
    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
    assertThat(board.reservedByOther(gather(8), rival, platform.now)).isTrue();
    for (int step = 1; step <= 10; step++) {
      position.set(new Location(world, 0.5D + step * 0.5D, 64D, 0.5D));
      pulse(1_000L);
    }
    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
    assertThat(board.reservedByOther(gather(8), rival, platform.now)).isTrue();
    position.set(at(7, 64, 0));
    pulse(250L);

    assertThat(acts).containsExactly("gather");
    assertThat(board.reservedByOther(gather(8), rival, platform.now)).isFalse();
    assertThat(board.reserve(gather(8), rival, platform.now, GolemTaskBoard.RESERVATION_MILLIS)).isTrue();
  }

  @Test
  void aCoreTargetReservedByAnotherGolemIsSkipped() {
    List<Boolean> skipped = new ArrayList<>();
    List<String> acts = new ArrayList<>();
    assertThat(board.reserve(gather(1), UUID.randomUUID(), platform.now, GolemTaskBoard.RESERVATION_MILLIS)).isTrue();
    coreSeeker(golem -> {
      GolemTask candidate = gather(1);
      skipped.add(golem.skips(candidate));
      return Optional.of(candidate);
    }, (golem, task) -> {
      acts.add(task.type());
      return GolemActResult.DONE;
    });
    brain.start();

    platform.runDelayed();

    assertThat(skipped).containsExactly(true);
    assertThat(acts).isEmpty();
    assertThat(brain.state()).isEqualTo(GolemBrainState.SEEK);
  }

  @Test
  void coreTargetsBeyondTheWorkRadiusOfHomeAreIgnored() {
    List<String> seen = new ArrayList<>();
    coreSeeker(golem -> {
      Location home = golem.home();
      seen.add(home.getBlockX() + "," + home.getBlockY() + "," + home.getBlockZ() + " r" + golem.workRadius());
      seen.add(golem.inWorkRadius(at(31, 64, 0)) + "," + golem.inWorkRadius(at(33, 64, 0)));
      return Optional.of(gather(33));
    }, (golem, task) -> GolemActResult.DONE);
    brain.start();

    platform.runDelayed();

    assertThat(seen).containsExactly("0,64,0 r32.0", "true,false");
    assertThat(brain.state()).isEqualTo(GolemBrainState.SEEK);
  }

  @Test
  void walkingOffAndBackKeepsTheActPace() {
    List<Long> actedAt = new ArrayList<>();
    collector((golem, task) -> {
      actedAt.add(platform.now);
      return GolemActResult.WORKING;
    });
    collectionNode(3);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    long arrival = platform.now;
    position.set(at(5, 64, 0));
    pulse(250L);
    position.set(at(0, 64, 0));
    pulse(250L);
    assertThat(actedAt).containsExactly(arrival);
    pulse(250L);
    assertThat(actedAt).containsExactly(arrival);
    pulse(250L);

    assertThat(actedAt).containsExactly(arrival, arrival + 1_000L);
  }

  @Test
  void coreBehaviorsTickEveryWorkingPulseEvenWhilePursuingAJob() {
    List<String> ticks = new ArrayList<>();
    tickingCore(golem -> ticks.add(golem.core().id() + ":" + golem.targetEntity().isPresent()));
    brain.start();

    platform.runDelayed();
    pulse(250L);

    assertThat(ticks).containsExactly("gatherer:false", "gatherer:false");
    assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);

    brain.handle().setMode(GolemMode.FOLLOW);
    pulse(250L);

    assertThat(ticks).hasSize(2);
  }

  @Test
  void aThrowingCoreTickIsLoggedOnceWithItsOwnMessageAndTheGolemKeepsWorking() {
    tickingCore(golem -> {
      throw new IllegalStateException("tick failed");
    });
    try (MockedStatic<Adapt> adapt = mockStatic(Adapt.class)) {
      brain.start();

      platform.runDelayed();
      pulse(250L);
      pulse(250L);

      assertThat(brain.state()).isEqualTo(GolemBrainState.MOVE);
      adapt.verify(() -> Adapt.warn(contains("failed to run its gatherer core tick"), any(Throwable.class)), times(1));
      adapt.verify(() -> Adapt.warn(contains("failed to find work"), any(Throwable.class)), never());
    }
  }

  private void tickingCore(Consumer<GolemContext> tick) {
    GolemCoreBehavior behavior = new GolemCoreBehavior() {
      @Override
      public Adaptation<?> policy() {
        return nodePolicy;
      }

      @Override
      public Optional<GolemTask> seek(GolemContext golem) {
        return Optional.of(GolemTask.builder("pickup", GolemTarget.block(WORLD, 12, 64, 0)).build());
      }

      @Override
      public GolemActResult act(GolemContext golem, GolemTask task) {
        return GolemActResult.WORKING;
      }

      @Override
      public void tick(GolemContext golem) {
        tick.accept(golem);
      }
    };
    GolemCoreBehaviors.register(GolemCoreType.GATHERER, behavior);
    cores.add(behavior);
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

  @Test
  void aGolemThatNeedsARodFetchesOneFromItsHomeChestBeforeItClaimsTheWork() {
    Inventory chest = homeChest(27);
    chest.setItem(4, GolemTestItems.stack(Material.STICK, 1));
    chest.setItem(5, GolemTestItems.stack(Material.FISHING_ROD, 1));
    List<String> acted = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      acted.add(golem.heldTool().map(tool -> tool.getType().name()).orElse("none"));
      return GolemActResult.WORKING;
    });
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    GolemTask task = pickup(4, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();
    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
    assertThat(task.holder(platform.now)).isEmpty();

    position.set(at(-5, 64, 0));
    pulse(250L);
    assertThat(controller.calls).contains("interact:-6,64,0");
    pulse(1_000L);
    assertThat(chest.getItem(5)).isNull();
    assertThat(chest.getItem(4).getType()).isEqualTo(Material.STICK);
    assertThat(brain.handle().state().held()).isNotNull();
    assertThat(GolemState.read(brain.handle().entity()).orElseThrow().held()).isEqualTo(brain.handle().state().held());

    position.set(at(4, 64, 0));
    pulse(1_000L);
    assertThat(acted).containsExactly("FISHING_ROD");
    assertThat(brain.handle().toolNote()).isNull();
  }

  @Test
  void aToolFetchHoldsTheLidFromArrivalAndTouchesItAgainWhenTheToolIsTaken() {
    Inventory chest = homeChest(27);
    chest.setItem(0, GolemTestItems.stack(Material.FISHING_ROD, 1));
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.WORKING);
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    board.offer(pickup(4, 64, 0), 16);
    brain.start();
    platform.runDelayed();

    position.set(at(-5, 64, 0));
    pulse(250L);
    assertThat(platform.lidVisits).containsExactly("-6,64,0 35");

    pulse(1_000L);
    assertThat(platform.lidVisits).containsExactly("-6,64,0 35", "-6,64,0 15");
    assertThat(chest.getItem(0)).isNull();
  }

  @Test
  void aToolFetchThatFindsNothingTakesNothingAndLeavesTheLidAlone() {
    Inventory chest = homeChest(27);
    chest.setItem(0, GolemTestItems.stack(Material.FISHING_ROD, 1));
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.WORKING);
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    board.offer(pickup(4, 64, 0), 16);
    brain.start();
    platform.runDelayed();

    chest.setItem(0, null);
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);

    assertThat(platform.lidVisits).containsExactly("-6,64,0 35");
    assertThat(brain.handle().state().held()).isNull();
  }

  @Test
  void withoutARodAnywhereTheGolemIdlesWithTheNoteAndNeverWorks() {
    List<String> acted = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      acted.add("act");
      return GolemActResult.WORKING;
    });
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    GolemTask task = pickup(4, 64, 0);
    board.offer(task, 16);
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 12; i++) {
      pulse(1_000L);
    }

    assertThat(acted).isEmpty();
    assertThat(brain.handle().toolNote()).isEqualTo(ROD_NOTE);
    assertThat(controller.calls).noneMatch(call -> call.startsWith("move:4.5"));
  }

  @Test
  void aHomeChestWithoutARodLeavesTheGolemIdleWithTheNote() {
    Inventory chest = homeChest(27);
    chest.setItem(0, GolemTestItems.stack(Material.COD, 3));
    List<String> acted = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      acted.add("act");
      return GolemActResult.WORKING;
    });
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    board.offer(pickup(4, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);

    assertThat(acted).isEmpty();
    assertThat(brain.handle().toolNote()).isEqualTo(ROD_NOTE);
    assertThat(controller.calls).noneMatch(call -> call.startsWith("move:-5.5"));
  }

  @Test
  void tasksThatNeedAMissingToolAreSkippedInOnePulseSoOtherWorkGoesOn() {
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.DONE);
    behavior.tools = (golem, task) -> task.target().blockX() < 8 ? new GolemToolPlan(List.of(ROD), false) : GolemToolPlan.NONE;
    collectionNode(1);
    List<GolemTask> gated = new ArrayList<>();
    for (int x = 1; x <= 6; x++) {
      GolemTask task = pickup(x, 64, 0);
      gated.add(task);
      board.offer(task, 16);
    }
    GolemTask free = pickup(9, 64, 0);
    board.offer(free, 16);
    brain.start();

    platform.runDelayed();

    assertThat(free.heldBy(brain.handle().id(), platform.now)).isTrue();
    assertThat(controller.lastMove()).isEqualTo("9.5,64.5,0.5");
    assertThat(gated).allSatisfy(task -> assertThat(task.holder(platform.now)).isEmpty());
    assertThat(brain.handle().toolNote()).isEqualTo(ROD_NOTE);
  }

  @Test
  void linkingAHomeChestEndsTheWaitForAMissingTool() {
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.WORKING);
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    GolemTask task = pickup(4, 64, 0);
    board.offer(task, 16);
    brain.start();
    platform.runDelayed();
    assertThat(brain.handle().toolNote()).isEqualTo(ROD_NOTE);

    Inventory chest = homeChest(27);
    chest.setItem(0, GolemTestItems.stack(Material.FISHING_ROD, 1));
    pulse(1_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.DELIVER);
    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
  }

  @Test
  void anOptionalToolThatIsMissingLetsTheWorkGoOnWithoutItAndShowsTheNote() {
    List<String> acted = new ArrayList<>();
    ScriptedBehavior behavior = collector((golem, task) -> {
      acted.add(golem.toolInUse().map(tool -> tool.getType().name()).orElse("free"));
      return GolemActResult.DONE;
    });
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(NETHERITE), true);
    collectionNode(1);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    pulse(1_000L);

    assertThat(acted).containsExactly("free");
    assertThat(brain.handle().toolNote()).isEqualTo(NETHERITE.note());
  }

  @Test
  void eachUseWearsTheHeldToolAndABreakRemovesItAndSendsTheGolemForTheNextOne() {
    try (MockedStatic<GolemToolEnchants> enchants = mockStatic(GolemToolEnchants.class)) {
      Inventory chest = homeChest(27);
      chest.setItem(2, GolemTestItems.tool(Material.FISHING_ROD, 0, 64));
      assertThat(brain.handle().setHeldEncoded(platform.encodeItem(GolemTestItems.tool(Material.FISHING_ROD, 62, 64)))).isTrue();
      List<Integer> damage = new ArrayList<>();
      ScriptedBehavior behavior = collector((golem, task) -> {
        golem.wearTool();
        damage.add(golem.heldTool().map(GolemTestItems::damage).orElse(-1));
        return GolemActResult.WORKING;
      });
      behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
      collectionNode(1);
      board.offer(pickup(1, 64, 0), 16);
      brain.start();

      platform.runDelayed();
      pulse(1_000L);
      assertThat(damage).containsExactly(63, -1);
      assertThat(brain.handle().state().held()).isNull();
      verify(brain.handle().entity()).broadcastSlotBreak(EquipmentSlot.HAND);

      pulse(1_000L);
      assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
      position.set(at(-5, 64, 0));
      pulse(250L);
      pulse(1_000L);
      assertThat(chest.getItem(2)).isNull();
      assertThat(GolemTestItems.damage(platform.decodeItem(brain.handle().state().held()))).isZero();
    }
  }

  @Test
  void fetchingABetterToolPutsTheHeldOneBackInItsSlot() {
    Inventory chest = homeChest(27);
    chest.setItem(3, GolemTestItems.stack(Material.NETHERITE_PICKAXE, 1));
    assertThat(brain.handle().setHeldEncoded(platform.encodeItem(GolemTestItems.stack(Material.DIAMOND_PICKAXE, 1)))).isTrue();
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.WORKING);
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(NETHERITE), true);
    collectionNode(1);
    board.offer(pickup(1, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);

    assertThat(chest.getItem(3).getType()).isEqualTo(Material.DIAMOND_PICKAXE);
    assertThat(platform.decodeItem(brain.handle().state().held()).getType()).isEqualTo(Material.NETHERITE_PICKAXE);
    assertThat(GolemTestItems.count(chest, Material.NETHERITE_PICKAXE)).isZero();
  }

  @Test
  void aGolemReleasedOnItsWayToTheChestTakesNothing() {
    Inventory chest = homeChest(27);
    chest.setItem(0, GolemTestItems.stack(Material.FISHING_ROD, 1));
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.WORKING);
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    board.offer(pickup(4, 64, 0), 16);
    brain.start();
    platform.runDelayed();
    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");

    brain.handle().retire();
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);
    pulse(1_000L);

    assertThat(chest.getItem(0).getType()).isEqualTo(Material.FISHING_ROD);
    assertThat(brain.handle().state().held()).isNull();
  }

  @Test
  void theHeldToolShowsInTheMainHandWithoutADropChanceAndAForeignItemFallsOut() {
    EntityEquipment equipment = mock(EntityEquipment.class);
    ItemStack ingot = GolemTestItems.stack(Material.COPPER_INGOT, 2);
    lenient().when(equipment.getItemInMainHand()).thenReturn(ingot);
    lenient().when(brain.handle().entity().getEquipment()).thenReturn(equipment);
    Inventory chest = homeChest(27);
    chest.setItem(0, GolemTestItems.stack(Material.FISHING_ROD, 1));
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.WORKING);
    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    collectionNode(1);
    board.offer(pickup(4, 64, 0), 16);
    brain.start();

    platform.runDelayed();
    position.set(at(-5, 64, 0));
    pulse(250L);
    pulse(1_000L);

    verify(equipment).setItemInMainHand(argThat(item -> item != null && item.getType() == Material.FISHING_ROD));
    verify(equipment).setItemInMainHandDropChance(0F);
    verify(world).dropItemNaturally(any(Location.class), argThat(item -> item == ingot));
  }

  @Test
  void aTideRuneWidensTheWorkRadiusAroundHome() {
    runes(new GolemRuneEffects(8D, 0, 1D, 0D));
    List<String> seen = new ArrayList<>();
    coreSeeker(golem -> {
      seen.add("r" + golem.workRadius());
      seen.add(golem.inWorkRadius(at(39, 64, 0)) + "," + golem.inWorkRadius(at(41, 64, 0)));
      return Optional.empty();
    }, (golem, task) -> GolemActResult.DONE);
    brain.start();

    platform.runDelayed();

    assertThat(seen).containsExactly("r40.0", "true,false");
  }

  @Test
  void aGolemClaimsTasksOutToTheEdgeOfARadiusThirtyTwoNodeArea() {
    collector((golem, task) -> GolemActResult.DONE);
    platform.nodes.put(COLLECTION, NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(32, 32, 32)).build());
    GolemTask edge = pickup(31, 64, 0);
    board.offer(edge, 16);
    brain.start();

    platform.runDelayed();

    assertThat(edge.heldBy(brain.handle().id(), platform.now)).isTrue();
  }

  @Test
  void aTideGolemWorkingOutAtTheEdgeOfARadiusThirtyTwoAreaIsNotLeashedHome() {
    runes(new GolemRuneEffects(12D, 0, 1D, 0D));
    collector((golem, task) -> GolemActResult.DONE);
    platform.nodes.put(COLLECTION, NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(32, 32, 32)).build());
    GolemTask edge = pickup(43, 64, 0);
    board.offer(edge, 16);
    position.set(at(40, 64, 0));
    brain.start();

    platform.runDelayed();

    assertThat(edge.heldBy(brain.handle().id(), platform.now)).isTrue();
  }

  @Test
  void aTideRuneLetsTheGolemClaimBoardTasksFartherFromHome() {
    collector((golem, task) -> GolemActResult.DONE);
    collectionNode(3);
    GolemTask far = pickup(36, 64, 0);
    board.offer(far, 16);
    brain.start();

    platform.runDelayed();
    assertThat(far.holder(platform.now)).isEmpty();
    runes(new GolemRuneEffects(8D, 0, 1D, 0D));
    pulse(1_000L);

    assertThat(far.heldBy(brain.handle().id(), platform.now)).isTrue();
  }

  @Test
  void withoutAnOrderRuneOnlyTheFirstChannelOfTheGolemCounts() {
    deposit(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).channel(DyeColor.RED).build());
    chest(DEPOSIT.x(), DEPOSIT.y(), DEPOSIT.z(), 27);
    homeChest(27);
    carry(Material.WHEAT, 20);
    brain.handle().setChannels(ChannelMask.of(DyeColor.WHITE, DyeColor.RED));
    brain.start();

    platform.runDelayed();

    assertThat(controller.lastMove()).isEqualTo("-5.5,64.5,0.5");
  }

  @Test
  void anOrderRuneLetsTheGolemDeliverOnItsExtraChannels() {
    runes(new GolemRuneEffects(0D, 1, 1D, 0D));
    deposit(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).channel(DyeColor.RED).build());
    chest(DEPOSIT.x(), DEPOSIT.y(), DEPOSIT.z(), 27);
    homeChest(27);
    carry(Material.WHEAT, 20);
    brain.handle().setChannels(ChannelMask.of(DyeColor.WHITE, DyeColor.RED));
    brain.start();

    platform.runDelayed();

    assertThat(controller.lastMove()).isEqualTo("0.5,64.5,6.5");
  }

  @Test
  void anOrderRuneLetsTheGolemClaimTasksOnItsExtraChannels() {
    collector((golem, task) -> GolemActResult.DONE);
    platform.nodes.put(COLLECTION, NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).channel(DyeColor.RED).build());
    GolemTask task = pickup(8, 64, 0);
    board.offer(task, 16);
    brain.handle().setChannels(ChannelMask.of(DyeColor.WHITE, DyeColor.RED));
    brain.start();

    platform.runDelayed();
    assertThat(task.holder(platform.now)).isEmpty();
    runes(new GolemRuneEffects(0D, 1, 1D, 0D));
    pulse(1_000L);

    assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue();
  }

  @Test
  void anOrderRuneShortensTheDelayBeforeAGolemRetriesAFailedJob() {
    runes(new GolemRuneEffects(0D, 2, 0.5D, 0D));
    List<Long> acts = new ArrayList<>();
    coreSeeker(golem -> Optional.of(GolemTask.builder("pickup", GolemTarget.block(WORLD, 1, 64, 0)).build()), (golem, task) -> {
      acts.add(platform.now);
      return GolemActResult.FAILED;
    });
    brain.start();

    platform.runDelayed();
    for (int i = 0; i < 4; i++) {
      pulse(1_000L);
    }
    assertThat(acts).hasSize(1);
    pulse(1_000L);

    assertThat(acts).hasSize(2);
    assertThat(acts.get(1) - acts.get(0)).isEqualTo(5_000L);
  }

  @Test
  void entropyBonusYieldReachesOnlyHarvestingCores() {
    runes(new GolemRuneEffects(0D, 0, 1D, 0.15D));
    List<Double> yields = new ArrayList<>();
    coreSeeker(golem -> {
      yields.add(golem.bonusYield());
      return Optional.empty();
    }, (golem, task) -> GolemActResult.DONE);
    GolemCoreBehavior breaker = new GolemCoreBehavior() {
      @Override
      public Adaptation<?> policy() {
        return nodePolicy;
      }

      @Override
      public Optional<GolemTask> seek(GolemContext golem) {
        yields.add(golem.bonusYield());
        return Optional.empty();
      }

      @Override
      public GolemActResult act(GolemContext golem, GolemTask task) {
        return GolemActResult.DONE;
      }
    };
    GolemCoreBehaviors.register(GolemCoreType.BREAKER, breaker);
    FakeMastery mastery = new FakeMastery();
    mastery.perks = new GolemMastery.Perks(1, true, false, 0D);
    GolemMastery.register(mastery);
    try {
      brain.handle().setCores(List.of(GolemCoreType.GATHERER, GolemCoreType.BREAKER));
      brain.handle().setMasterwork(true);
      brain.start();

      platform.runDelayed();
    } finally {
      GolemCoreBehaviors.unregister(GolemCoreType.BREAKER, breaker);
      GolemMastery.unregister(mastery);
    }

    assertThat(yields).containsExactly(0D, 0.15D);
  }

  @Test
  void aSecondCoreRestsUntilItsOwnerHoldsTheDualCorePerk() {
    List<GolemCoreType> sought = new ArrayList<>();
    coreSeeker(golem -> {
      sought.add(golem.core());
      return Optional.empty();
    }, (golem, task) -> GolemActResult.DONE);
    GolemCoreBehavior breaker = new GolemCoreBehavior() {
      @Override
      public Adaptation<?> policy() {
        return nodePolicy;
      }

      @Override
      public Optional<GolemTask> seek(GolemContext golem) {
        sought.add(golem.core());
        return Optional.empty();
      }

      @Override
      public GolemActResult act(GolemContext golem, GolemTask task) {
        return GolemActResult.DONE;
      }
    };
    GolemCoreBehaviors.register(GolemCoreType.BREAKER, breaker);
    FakeMastery mastery = new FakeMastery();
    GolemMastery.register(mastery);
    try {
      brain.handle().setCores(List.of(GolemCoreType.GATHERER, GolemCoreType.BREAKER));
      brain.handle().setMasterwork(true);
      brain.start();
      platform.runDelayed();
      assertThat(sought).containsExactly(GolemCoreType.GATHERER);

      mastery.perks = new GolemMastery.Perks(1, true, false, 0D);
      pulse(1_000L);
    } finally {
      GolemCoreBehaviors.unregister(GolemCoreType.BREAKER, breaker);
      GolemMastery.unregister(mastery);
    }

    assertThat(sought).containsExactly(GolemCoreType.GATHERER, GolemCoreType.GATHERER, GolemCoreType.BREAKER);
  }

  @Test
  void changingANodeChannelReleasesAClaimBeforeAnotherActOrRenewal() {
    List<GolemTask> acted = new ArrayList<>();
    collector((golem, task) -> {
      acted.add(task);
      return GolemActResult.WORKING;
    });
    collectionNode(3);
    GolemTask task = pickup(20, 64, 0);
    board.offer(task, 16);
    brain.start();
    platform.runDelayed();
    assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue();

    platform.nodes.put(COLLECTION, platform.nodes.get(COLLECTION).toBuilder().channel(DyeColor.RED).build());
    position.set(at(20, 64, 0));
    pulse(250L);

    assertThat(task.holder(platform.now)).isEmpty();
    assertThat(acted).isEmpty();
  }

  @Test
  void changingTheGolemChannelsReleasesAnExistingClaim() {
    collectionNode(3);
    platform.nodes.put(COLLECTION, platform.nodes.get(COLLECTION).toBuilder().channel(DyeColor.RED).build());
    brain.handle().state().setChannels(ChannelMask.of(DyeColor.RED));
    List<GolemTask> acted = new ArrayList<>();
    collector((golem, task) -> {
      acted.add(task);
      return GolemActResult.DONE;
    });
    GolemTask task = pickup(20, 64, 0);
    board.offer(task, 16);
    brain.start();
    platform.runDelayed();
    assertThat(task.heldBy(brain.handle().id(), platform.now)).isTrue();

    brain.handle().state().setChannels(ChannelMask.NONE);
    position.set(at(20, 64, 0));
    pulse(250L);

    assertThat(task.holder(platform.now)).isEmpty();
    assertThat(acted).isEmpty();
  }

  @Test
  void theCachedWorkReasonTracksMovingActingAndMissingTools() {
    ScriptedBehavior behavior = collector((golem, task) -> GolemActResult.WORKING);
    collectionNode(3);
    board.offer(pickup(20, 64, 0), 16);
    brain.start();
    platform.runDelayed();
    assertThat(brain.handle().workStatus()).isEqualTo(GolemWorkStatus.MOVING);

    position.set(at(20, 64, 0));
    pulse(250L);
    assertThat(brain.handle().workStatus()).isEqualTo(GolemWorkStatus.WORKING);

    behavior.tools = (golem, task) -> new GolemToolPlan(List.of(ROD), false);
    pulse(1_000L);
    assertThat(brain.handle().workStatus()).isEqualTo(GolemWorkStatus.NEEDS_TOOL);
    assertThat(brain.handle().toolNote()).isEqualTo(ROD_NOTE);
  }

  private void pulse(long millis) {
    platform.now += millis;
    platform.runDelayed();
  }

  private ScriptedBehavior collector(BiFunction<GolemContext, GolemTask, GolemActResult> act) {
    ScriptedBehavior behavior = new ScriptedBehavior(act);
    NodeBehaviors.register(NodeType.COLLECTION, behavior);
    registered.add(behavior);
    return behavior;
  }

  private void coreSeeker(Function<GolemContext, Optional<GolemTask>> seek, BiFunction<GolemContext, GolemTask, GolemActResult> act) {
    GolemCoreBehavior behavior = new GolemCoreBehavior() {
      @Override
      public Adaptation<?> policy() {
        return nodePolicy;
      }

      @Override
      public Optional<GolemTask> seek(GolemContext golem) {
        return seek.apply(golem);
      }

      @Override
      public GolemActResult act(GolemContext golem, GolemTask task) {
        return act.apply(golem, task);
      }
    };
    GolemCoreBehaviors.register(GolemCoreType.GATHERER, behavior);
    cores.add(behavior);
  }

  private void runes(GolemRuneEffects effects) {
    GolemRunes.Augments fixed = new GolemRunes.Augments() {
      @Override
      public List<String> describe(GolemRune rune, GolemMastery.Perks perks) {
        return List.of();
      }

      @Override
      public GolemRuneLayout layout(GolemBody body, UUID owner, boolean masterwork) {
        return GolemRuneLayout.NONE;
      }

      @Override
      public GolemRuneEffects effects(GolemHandle handle) {
        return effects;
      }
    };
    GolemRunes.register(fixed);
    augments.add(fixed);
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

  private static GolemTask stash(int z) {
    return GolemTask.builder(COLLECTION, NodeType.COLLECTION, "stash", GolemTarget.block(WORLD, 0, 64, z)).build();
  }

  private static GolemTask gather(int x) {
    return GolemTask.builder("gather", GolemTarget.block(WORLD, x, 64, 0)).build();
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
    lenient().when(block.getState(false)).thenReturn(chest);
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

  private final class ScriptedBehavior implements NodeBehavior {
    private final BiFunction<GolemContext, GolemTask, GolemActResult> act;
    private int intervalTicks = DEFAULT_ACT_INTERVAL_TICKS;
    private BiFunction<GolemContext, List<ItemStack>, Optional<GolemTask>> route = (golem, carried) -> Optional.empty();
    private BiFunction<GolemContext, GolemTask, GolemToolPlan> tools = (golem, task) -> GolemToolPlan.NONE;
    private boolean deliversFirst;

    private ScriptedBehavior(BiFunction<GolemContext, GolemTask, GolemActResult> act) {
      this.act = act;
    }

    @Override
    public Adaptation<?> policy() {
      return nodePolicy;
    }

    @Override
    public void scan(NodeContext node, ScanBudget budget) {
    }

    @Override
    public GolemActResult act(GolemContext golem, GolemTask task) {
      return act.apply(golem, task);
    }

    @Override
    public int actIntervalTicks(GolemContext golem, GolemTask task) {
      return intervalTicks;
    }

    @Override
    public Optional<GolemTask> route(GolemContext golem, NodeKey node, List<ItemStack> carried) {
      return route.apply(golem, carried);
    }

    @Override
    public boolean deliversBeforeClaiming() {
      return deliversFirst;
    }

    @Override
    public GolemToolPlan tools(GolemContext golem, GolemTask task) {
      return tools.apply(golem, task);
    }
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

  private static final class RecordingStats implements GolemWorkStats.Sink {
    private final List<String> calls;

    private RecordingStats(List<String> calls) {
      this.calls = calls;
    }

    @Override
    public void completed(GolemHandle handle) {
      calls.add("completed");
    }

    @Override
    public void moved(GolemHandle handle, long items) {
      calls.add("moved:" + items);
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
