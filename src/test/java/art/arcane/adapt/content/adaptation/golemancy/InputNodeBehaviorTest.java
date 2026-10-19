package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class InputNodeBehaviorTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000026d2");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000026f2");
  private static final NodeKey KEY = new NodeKey(SorterYard.WORLD, 0, 64, 0, BlockFace.UP);
  private static final long NOW = 50_000L;
  private static final NodeTagLookup NO_TAGS = new NodeTagLookup() {
    @Override
    public boolean exists(String tag) {
      return false;
    }

    @Override
    public boolean tagged(String tag, Material material) {
      return false;
    }
  };

  private final SorterYard yard = new SorterYard();
  private final SorterItems items = yard.items;
  private final GolemSorterCore.Config config = new GolemSorterCore.Config();
  private final SorterIndex index = new SorterIndex(() -> 64, () -> 30_000L);
  private final SorterPlans plans = new SorterPlans();
  private final SorterFetches fetches = new SorterFetches(() -> 120_000L);
  private final List<GolemTask> posted = new ArrayList<>();
  private final List<ItemStack> cargo = new ArrayList<>();
  private final List<Double> xp = new ArrayList<>();
  private final List<String> interactions = new ArrayList<>();
  private final Map<UUID, Player> players = new HashMap<>();
  private final Set<UUID> away = new HashSet<>();
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.INPUT, BlockFace.UP).build();
  private GolemSorterCore core;
  private InputNodeBehavior behavior;
  private Inventory input;
  private int capacity = 256;
  private int level = 1;
  private long now = NOW;
  private Entity target;
  private MockedStatic<SorterFeedback> feedback;

  @BeforeEach
  void setUp() {
    core = mock(GolemSorterCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.groups()).thenReturn(Map.of());
    lenient().when(core.ownerLevel(OWNER)).thenAnswer(invocation -> level);
    yard.permitThrough(core);
    SorterNetwork network = new SorterNetwork(core, yard.space(), index, NO_TAGS, items::variant, () -> 2_000L);
    behavior = new InputNodeBehavior(core, network, plans, fetches, index, players::get);
    input = yard.chest(0, 64, 0);
    feedback = mockStatic(SorterFeedback.class);
  }

  @AfterEach
  void close() {
    feedback.close();
  }

  @Test
  void itActsForTheSorterCoreAndDeliversBeforeClaimingMoreWork() {
    assertThat(behavior.policy()).isSameAs(core);
    assertThat(behavior.deliversBeforeClaiming()).isTrue();
  }

  @Test
  void aScanPostsASortJobAtTheInputForEachItemThatHasADestination() {
    fill(input, items.item(Material.COBBLESTONE, 20), items.item(Material.DIRT, 5), items.item(Material.STONE, 3));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    yard.chest(0, 64, 4, items.item(Material.DIRT, 1));

    behavior.scan(node(), budget(true));

    assertThat(jobs()).containsExactly("sort 0,64,0 COBBLESTONE", "sort 0,64,0 DIRT");
  }

  @Test
  void aScanWithdrawsJobsWhoseItemLostItsDestinationOrLeftTheInput() {
    fill(input, items.item(Material.COBBLESTONE, 20), items.item(Material.DIRT, 5));
    Inventory cobble = yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    yard.chest(0, 64, 4, items.item(Material.DIRT, 1));
    behavior.scan(node(), budget(true));

    cobble.setItem(0, null);
    input.setItem(1, null);
    behavior.scan(node(), budget(true));
    assertThat(jobs()).containsExactly("sort 0,64,0 COBBLESTONE");

    now += 2_000L;
    behavior.scan(node(), budget(true));

    assertThat(posted).isEmpty();
  }

  @Test
  void anOwnerWithoutTheCorePostsNothingAndClearsOpenJobs() {
    fill(input, items.item(Material.COBBLESTONE, 20));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    behavior.scan(node(), budget(true));
    assertThat(posted).hasSize(1);

    level = 0;
    behavior.scan(node(), budget(true));

    assertThat(posted).isEmpty();
  }

  @Test
  void aScanWithoutTaskRoomPostsNothingNew() {
    fill(input, items.item(Material.COBBLESTONE, 20));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));

    behavior.scan(node(), budget(false));

    assertThat(posted).isEmpty();
  }

  @Test
  void theSortActWindsUpThenTakesTheJobsItemFirstUpToTheTripsStacks() {
    level = 3;
    fill(input, items.item(Material.DIRT, 10), items.item(Material.COBBLESTONE, 64), items.item(Material.COBBLESTONE, 64),
        items.item(Material.COBBLESTONE, 64));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    yard.chest(0, 64, 4, items.item(Material.DIRT, 1));
    GolemTask sort = task(SorterRules.SORT, 0, 64, 0, items.item(Material.COBBLESTONE, 1));

    assertThat(behavior.act(golem(0), sort)).isEqualTo(GolemActResult.WORKING);
    assertThat(interactions).containsExactly("0,64,0");
    assertThat(cargo).isEmpty();

    assertThat(behavior.act(golem(1), sort)).isEqualTo(GolemActResult.DONE);
    assertThat(carried(Material.COBBLESTONE)).isEqualTo(128);
    assertThat(carried(Material.DIRT)).isZero();
    assertThat(SorterItems.count(input, Material.COBBLESTONE)).isEqualTo(64);
    assertThat(SorterItems.count(input, Material.DIRT)).isEqualTo(10);
  }

  @Test
  void theSortActFillsTheTripWithOtherItemsThatHaveADestinationAndKeepsWhatDoesNotFit() {
    level = 5;
    capacity = 40;
    fill(input, items.item(Material.COBBLESTONE, 30), items.item(Material.STONE, 5), items.item(Material.DIRT, 20));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    yard.chest(0, 64, 4, items.item(Material.DIRT, 1));

    assertThat(behavior.act(golem(1), task(SorterRules.SORT, 0, 64, 0, items.item(Material.COBBLESTONE, 1)))).isEqualTo(GolemActResult.DONE);

    assertThat(carried(Material.COBBLESTONE)).isEqualTo(30);
    assertThat(carried(Material.DIRT)).isEqualTo(10);
    assertThat(SorterItems.count(input, Material.DIRT)).isEqualTo(10);
    assertThat(SorterItems.count(input, Material.STONE)).isEqualTo(5);
  }

  @Test
  void aLoadedGolemTakesNothingMoreAndAnEmptyTakeFails() {
    fill(input, items.item(Material.COBBLESTONE, 30));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    cargo.add(items.item(Material.DIRT, 4));
    GolemTask sort = task(SorterRules.SORT, 0, 64, 0, items.item(Material.COBBLESTONE, 1));

    assertThat(behavior.act(golem(0), sort)).isEqualTo(GolemActResult.FAILED);
    assertThat(interactions).isEmpty();
    assertThat(SorterItems.count(input, Material.COBBLESTONE)).isEqualTo(30);

    level = 3;
    input.setItem(0, null);
    assertThat(behavior.act(golem(1), sort)).isEqualTo(GolemActResult.FAILED);
    cargo.clear();
    assertThat(behavior.act(golem(1), sort)).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void aProtectedInputBlocksTheJob() {
    fill(input, items.item(Material.COBBLESTONE, 30));
    GolemContext golem = golem(0);
    lenient().when(golem.canAccessChest(any(Location.class))).thenReturn(false);

    assertThat(behavior.act(golem, task(SorterRules.SORT, 0, 64, 0, items.item(Material.COBBLESTONE, 1)))).isEqualTo(GolemActResult.BLOCKED);
  }

  @Test
  void theRouteVisitsTheNearestDestinationAndItsDropDeliversEverythingThatBelongsThere() {
    yard.chest(0, 64, 4, items.item(Material.DIRT, 1));
    Inventory cobble = yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1), items.item(Material.SAND, 1));
    cargo.add(items.item(Material.DIRT, 5));
    cargo.add(items.item(Material.COBBLESTONE, 10));
    cargo.add(items.item(Material.SAND, 3));

    GolemTask drop = behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow();
    assertThat(describe(drop)).isEqualTo("drop 3,64,0 -");
    assertThat(drop.node()).contains(KEY);

    assertThat(behavior.act(golem(0), drop)).isEqualTo(GolemActResult.WORKING);
    assertThat(behavior.act(golem(1), drop)).isEqualTo(GolemActResult.DONE);

    assertThat(SorterItems.count(cobble, Material.COBBLESTONE)).isEqualTo(11);
    assertThat(SorterItems.count(cobble, Material.SAND)).isEqualTo(4);
    assertThat(carried(Material.DIRT)).isEqualTo(5);
    assertThat(xp).containsExactly(0.6D);
    assertThat(describe(behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow())).isEqualTo("drop 0,64,4 -");
  }

  @Test
  void aDropPaysXpOnlyForStacksThatActuallyWentIn() {
    ItemStack[] nearlyFull = new ItemStack[27];
    for (int slot = 0; slot < 26; slot++) {
      nearlyFull[slot] = items.item(Material.COBBLESTONE, 64);
    }
    nearlyFull[26] = items.item(Material.COBBLESTONE, 60);
    Inventory cobble = yard.chest(3, 64, 0, nearlyFull);
    cargo.add(items.item(Material.COBBLESTONE, 10));
    cargo.add(items.item(Material.COBBLESTONE, 10));

    GolemTask drop = behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow();
    assertThat(describe(drop)).isEqualTo("drop 3,64,0 -");
    assertThat(behavior.act(golem(0), drop)).isEqualTo(GolemActResult.WORKING);
    assertThat(behavior.act(golem(1), drop)).isEqualTo(GolemActResult.DONE);

    assertThat(SorterItems.count(cobble, Material.COBBLESTONE)).isEqualTo(27 * 64);
    assertThat(carried(Material.COBBLESTONE)).isEqualTo(16);
    assertThat(xp).containsExactly(0.3D);
  }

  @Test
  void itemsWithNowhereToGoReturnToTheInputAndADropWithNothingToLeaveFails() {
    cargo.add(items.item(Material.STONE, 5));

    GolemTask back = behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow();
    assertThat(describe(back)).isEqualTo("return 0,64,0 -");
    assertThat(behavior.act(golem(1), back)).isEqualTo(GolemActResult.DONE);
    assertThat(SorterItems.count(input, Material.STONE)).isEqualTo(5);
    assertThat(cargo).isEmpty();
    assertThat(xp).isEmpty();

    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    cargo.add(items.item(Material.STONE, 5));
    assertThat(behavior.act(golem(1), task(SorterRules.DROP, 3, 64, 0, null))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void restockingTopsAFilterNodeUpFromAServedContainer() {
    level = 4;
    yard.chest(2, 64, 2, items.item(Material.TORCH, 4));
    yard.node(2, 64, 2, NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.UP).filterItems(List.of(items.item(Material.TORCH, 1)))
        .data(Map.of(SorterRules.RESTOCK_TARGET, "16")).build());
    Inventory storage = yard.chest(6, 64, 0, items.item(Material.TORCH, 64));

    behavior.scan(node(), budget(true));
    assertThat(jobs()).containsExactly("restock 6,64,0 TORCH");

    GolemTask restock = posted.getFirst();
    assertThat(behavior.act(golem(0), restock)).isEqualTo(GolemActResult.WORKING);
    assertThat(behavior.act(golem(1), restock)).isEqualTo(GolemActResult.DONE);
    assertThat(carried(Material.TORCH)).isEqualTo(12);
    assertThat(SorterItems.count(storage, Material.TORCH)).isEqualTo(52);

    GolemTask stock = behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow();
    assertThat(describe(stock)).isEqualTo("stock 2,64,2 TORCH");
    assertThat(behavior.act(golem(1), stock)).isEqualTo(GolemActResult.DONE);
    assertThat(SorterItems.count(yard.inventory(2, 64, 2), Material.TORCH)).isEqualTo(16);
    assertThat(cargo).isEmpty();
    assertThat(plans.get(GOLEM, NOW)).isEmpty();
  }

  @Test
  void fetchingBringsTheRequestedItemsToThePlayer() {
    level = 5;
    capacity = 64;
    Inventory storage = yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 64), items.item(Material.COBBLESTONE, 30));
    Player player = player(items.item(Material.COBBLESTONE, 30));
    fetches.request(player.getUniqueId(), KEY, items.item(Material.COBBLESTONE, 1), 80, NOW);

    behavior.scan(node(), budget(true));
    assertThat(jobs()).containsExactly("fetch 3,64,0 COBBLESTONE");

    GolemTask fetch = posted.getFirst();
    assertThat(behavior.act(golem(1), fetch)).isEqualTo(GolemActResult.DONE);
    assertThat(carried(Material.COBBLESTONE)).isEqualTo(64);
    assertThat(SorterItems.count(storage, Material.COBBLESTONE)).isEqualTo(30);
    assertThat(fetches.open(KEY, NOW)).singleElement().satisfies(request -> assertThat(remaining(request)).isEqualTo(16));

    GolemTask hand = behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow();
    assertThat(hand.type()).isEqualTo(SorterRules.HAND);
    assertThat(hand.target().entity()).isEqualTo(player.getUniqueId());
    target = player;

    assertThat(behavior.act(golem(1), hand)).isEqualTo(GolemActResult.DONE);
    assertThat(cargo).isEmpty();
    assertThat(SorterItems.count(player.getInventory(), Material.COBBLESTONE)).isEqualTo(64);
    verify(yard.world).dropItemNaturally(any(Location.class), argThat(item -> item.getType() == Material.COBBLESTONE && item.getAmount() == 30));
  }

  @Test
  void aFetchForAPlayerWhoLeftEndsWithoutDeliveringAndTheItemsAreSortedAgain() {
    level = 5;
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 64));
    Player player = player();
    SorterFetches.Request request = fetches.request(player.getUniqueId(), KEY, items.item(Material.COBBLESTONE, 1), 10, NOW);
    plans.fetch(GOLEM, request, items.item(Material.COBBLESTONE, 1), request.reserve(10), NOW);
    cargo.add(items.item(Material.COBBLESTONE, 10));
    target = null;
    players.remove(player.getUniqueId());

    assertThat(behavior.act(golem(1), handTask(player))).isEqualTo(GolemActResult.DONE);
    assertThat(carried(Material.COBBLESTONE)).isEqualTo(10);
    assertThat(plans.get(GOLEM, NOW)).isEmpty();
    assertThat(remaining(request)).isEqualTo(10);
    assertThat(describe(behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow())).isEqualTo("drop 3,64,0 -");
  }

  @Test
  void destinationsBeyondTheGolemsWorkRadiusAreLeftForOtherGolems() {
    level = 5;
    fill(input, items.item(Material.COBBLESTONE, 20), items.item(Material.DIRT, 5));
    yard.chest(20, 64, 0, items.item(Material.COBBLESTONE, 1));
    yard.chest(0, 64, 4, items.item(Material.DIRT, 1));
    GolemContext golem = golem(1);
    lenient().when(golem.inWorkRadius(any(Location.class))).thenAnswer(invocation -> invocation.<Location>getArgument(0).getX() < 10D);

    assertThat(behavior.act(golem, task(SorterRules.SORT, 0, 64, 0, items.item(Material.COBBLESTONE, 1)))).isEqualTo(GolemActResult.DONE);
    assertThat(carried(Material.COBBLESTONE)).isZero();
    assertThat(carried(Material.DIRT)).isEqualTo(5);

    cargo.add(items.item(Material.COBBLESTONE, 3));
    cargo.removeIf(item -> item.getType() == Material.DIRT);
    assertThat(describe(behavior.route(golem, KEY, List.copyOf(cargo)).orElseThrow())).isEqualTo("return 0,64,0 -");
  }

  @Test
  void fetchAndRestockJobsGoOutFirstAndPushOutTheCheapestSortJobWhenTheBoardIsFull() {
    level = 5;
    fill(input, items.item(Material.COBBLESTONE, 40), items.item(Material.DIRT, 3));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1), items.item(Material.DIRT, 1), items.item(Material.SAND, 64));
    behavior.scan(node(), budget(true));
    assertThat(jobs()).containsExactly("sort 0,64,0 COBBLESTONE", "sort 0,64,0 DIRT");

    Player player = player();
    fetches.request(player.getUniqueId(), KEY, items.item(Material.SAND, 1), 16, NOW);
    behavior.scan(node(), budget(false));

    assertThat(jobs()).containsExactly("fetch 3,64,0 SAND", "sort 0,64,0 COBBLESTONE");
    posted.clear();
    behavior.scan(node(), budget(true));
    assertThat(posted).extracting(GolemTask::type).containsExactly(SorterRules.FETCH, SorterRules.SORT, SorterRules.SORT);
  }

  @Test
  void aRestockAlreadyOnItsWayCountsAgainstTheDeficit() {
    level = 4;
    yard.chest(2, 64, 2, items.item(Material.TORCH, 4));
    yard.node(2, 64, 2, NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.UP).filterItems(List.of(items.item(Material.TORCH, 1)))
        .data(Map.of(SorterRules.RESTOCK_TARGET, "16")).build());
    yard.chest(6, 64, 0, items.item(Material.TORCH, 64));
    plans.restock(UUID.randomUUID(), new ContainerId(SorterYard.WORLD, 2, 64, 2), items.item(Material.TORCH, 1), 12, NOW);

    behavior.scan(node(), budget(true));
    assertThat(posted).isEmpty();

    plans.clear();
    plans.restock(UUID.randomUUID(), new ContainerId(SorterYard.WORLD, 2, 64, 2), items.item(Material.TORCH, 1), 8, NOW);
    behavior.scan(node(), budget(true));
    GolemTask restock = posted.getFirst();
    assertThat(behavior.act(golem(1), restock)).isEqualTo(GolemActResult.DONE);
    assertThat(carried(Material.TORCH)).isEqualTo(4);
  }

  @Test
  void aShulkerBoxInputNeverTakesAShulkerBoxBack() {
    yard.block(0, 64, 0);
    lenient().when(yard.block(0, 64, 0).getType()).thenReturn(Material.SHULKER_BOX);
    cargo.add(items.item(Material.SHULKER_BOX, 1));

    assertThat(behavior.route(golem(0), KEY, List.copyOf(cargo))).isEmpty();
    assertThat(behavior.act(golem(1), task(SorterRules.RETURN, 0, 64, 0, null))).isEqualTo(GolemActResult.FAILED);
    assertThat(SorterItems.count(input, Material.SHULKER_BOX)).isZero();
  }

  @Test
  void aPlayerInAnotherRegionKeepsTheFetchWaitingUntilTheirPlanIsDropped() {
    level = 5;
    Player player = player();
    SorterFetches.Request request = fetches.request(player.getUniqueId(), KEY, items.item(Material.COBBLESTONE, 1), 10, NOW);
    plans.fetch(GOLEM, request, items.item(Material.COBBLESTONE, 1), request.reserve(10), NOW);
    cargo.add(items.item(Material.COBBLESTONE, 10));
    away.add(player.getUniqueId());

    GolemTask wait = behavior.route(golem(0), KEY, List.copyOf(cargo)).orElseThrow();
    assertThat(wait.type()).isEqualTo(SorterRules.HAND);
    assertThat(wait.target().entity()).isEqualTo(player.getUniqueId());
    assertThat(wait.target().x()).isEqualTo(0.5D);
    target = null;
    assertThat(behavior.act(golem(1), wait)).isEqualTo(GolemActResult.WORKING);
    assertThat(plans.get(GOLEM, NOW)).isPresent();

    plans.forgetPlayer(player.getUniqueId());
    assertThat(behavior.act(golem(1), wait)).isEqualTo(GolemActResult.DONE);
    assertThat(remaining(request)).isEqualTo(10);
    verify(player, never()).getLocation();
    verify(player, never()).getWorld();
  }

  @Test
  void aCarryThatThrowsPutsTheItemsBackInTheirSlot() {
    fill(input, items.item(Material.COBBLESTONE, 20));
    yard.chest(3, 64, 0, items.item(Material.COBBLESTONE, 1));
    GolemContext golem = golem(1);
    lenient().when(golem.carry(any(ItemStack.class))).thenThrow(new IllegalStateException("cargo refused"));

    assertThatThrownBy(() -> behavior.act(golem, task(SorterRules.SORT, 0, 64, 0, items.item(Material.COBBLESTONE, 1))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(SorterItems.count(input, Material.COBBLESTONE)).isEqualTo(20);
  }

  private void fill(Inventory inventory, ItemStack... contents) {
    for (int slot = 0; slot < contents.length; slot++) {
      inventory.setItem(slot, contents[slot]);
    }
  }

  private int carried(Material type) {
    int total = 0;
    for (ItemStack item : cargo) {
      if (item.getType() == type) {
        total += item.getAmount();
      }
    }
    return total;
  }

  private Player player(ItemStack... held) {
    Player player = mock(Player.class, withSettings().strictness(Strictness.LENIENT));
    UUID id = UUID.randomUUID();
    lenient().when(player.getUniqueId()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.getWorld()).thenReturn(yard.world);
    lenient().when(player.getLocation()).thenReturn(new Location(yard.world, 1.5D, 64D, 1.5D));
    Inventory backing = items.inventory(1, held);
    PlayerInventory inventory = mock(PlayerInventory.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(inventory.addItem(any(ItemStack[].class))).thenAnswer(invocation -> backing.addItem(stacks(invocation.getArguments())));
    lenient().when(inventory.getStorageContents()).thenAnswer(invocation -> backing.getStorageContents());
    lenient().when(player.getInventory()).thenReturn(inventory);
    players.put(id, player);
    return player;
  }

  private static int remaining(SorterFetches.Request request) {
    int left = request.reserve(Integer.MAX_VALUE);
    request.release(left);
    return left;
  }

  private static ItemStack[] stacks(Object[] arguments) {
    return Arrays.copyOf(arguments, arguments.length, ItemStack[].class);
  }

  private NodeContext node() {
    NodeContext node = mock(NodeContext.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(node.owner()).thenReturn(OWNER);
    lenient().when(node.key()).thenReturn(KEY);
    lenient().when(node.record()).thenAnswer(invocation -> record);
    lenient().when(node.world()).thenReturn(yard.world);
    lenient().when(node.nowMillis()).thenAnswer(invocation -> now);
    lenient().when(node.anchor()).thenReturn(yard.block(0, 64, 0));
    lenient().when(node.owns(any(Block.class))).thenAnswer(invocation -> yard.access().owns(invocation.getArgument(0)));
    lenient().when(node.onlineOwner()).thenReturn(Optional.of(yard.owner));
    lenient().when(node.tasks()).thenAnswer(invocation -> List.copyOf(posted));
    lenient().when(node.task(anyString(), any(GolemTarget.class)))
        .thenAnswer(invocation -> GolemTask.builder(KEY, NodeType.INPUT, invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(node.post(any(GolemTask.class))).thenAnswer(invocation -> {
      GolemTask task = invocation.getArgument(0);
      if (posted.stream().noneMatch(open -> describe(open).equals(describe(task)))) {
        posted.add(task);
      }
      return true;
    });
    lenient().when(node.withdraw(any(GolemTask.class))).thenAnswer(invocation -> posted.remove(invocation.<GolemTask>getArgument(0)));
    return node;
  }

  private ScanBudget budget(boolean room) {
    ScanBudget budget = mock(ScanBudget.class);
    lenient().when(budget.hasTaskRoom()).thenReturn(room);
    return budget;
  }

  private GolemContext golem(int actIndex) {
    GolemContext golem = mock(GolemContext.class, withSettings().strictness(Strictness.LENIENT));
    Player owner = yard.owner;
    GolemHandle handle = mock(GolemHandle.class, withSettings().strictness(Strictness.LENIENT));
    lenient().when(handle.id()).thenReturn(GOLEM);
    Mob body = mock(Mob.class);
    lenient().when(body.getLocation()).thenReturn(new Location(yard.world, 0.5D, 65D, 0.5D));
    lenient().when(golem.handle()).thenReturn(handle);
    lenient().when(golem.golem()).thenReturn(body);
    lenient().when(golem.owner()).thenReturn(owner);
    lenient().when(golem.node()).thenAnswer(invocation -> Optional.of(record));
    lenient().when(golem.world()).thenReturn(yard.world);
    lenient().when(golem.nowMillis()).thenAnswer(invocation -> now);
    lenient().when(golem.actIndex()).thenReturn(actIndex);
    lenient().when(golem.owns(any(Location.class))).thenReturn(true);
    lenient().when(golem.canAccessChest(any(Location.class))).thenReturn(true);
    lenient().when(golem.inWorkRadius(any(Location.class))).thenReturn(true);
    lenient().when(golem.skips(any(GolemTask.class))).thenReturn(false);
    lenient().when(golem.container(any(Block.class))).thenAnswer(invocation -> yard.container(invocation.getArgument(0)));
    lenient().when(golem.ownedEntity(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(away.contains(invocation.<UUID>getArgument(0))
        ? null : players.get(invocation.<UUID>getArgument(0))));
    lenient().when(golem.targetEntity()).thenAnswer(invocation -> Optional.ofNullable(target));
    lenient().when(golem.carriedItems()).thenAnswer(invocation -> copies());
    lenient().when(golem.carried()).thenAnswer(invocation -> carriedCount());
    lenient().when(golem.capacity()).thenAnswer(invocation -> capacity);
    lenient().when(golem.room()).thenAnswer(invocation -> Math.max(0, capacity - carriedCount()));
    lenient().when(golem.carry(any(ItemStack.class))).thenAnswer(invocation -> carry(invocation.getArgument(0)));
    lenient().when(golem.take(any(), anyInt())).thenAnswer(invocation -> take(invocation.getArgument(0), invocation.getArgument(1)));
    lenient().when(golem.deliverUpTo(any(Inventory.class), any())).thenAnswer(invocation -> deliver(invocation.getArgument(0), invocation.getArgument(1), delivered -> {
    }));
    lenient().when(golem.deliverUpTo(any(Inventory.class), any(), any())).thenAnswer(invocation -> deliver(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    lenient().doAnswer(invocation -> {
      Block block = invocation.getArgument(0);
      interactions.add(block.getX() + "," + block.getY() + "," + block.getZ());
      return null;
    }).when(golem).interact(any(Block.class), anyInt());
    lenient().doAnswer(invocation -> xp.add(invocation.getArgument(0))).when(golem).workXp(anyDouble());
    return golem;
  }

  private List<ItemStack> copies() {
    List<ItemStack> copies = new ArrayList<>(cargo.size());
    for (ItemStack item : cargo) {
      copies.add(item.clone());
    }
    return copies;
  }

  private int carriedCount() {
    int total = 0;
    for (ItemStack item : cargo) {
      total += item.getAmount();
    }
    return total;
  }

  private Optional<ItemStack> carry(ItemStack stack) {
    int taking = Math.min(stack.getAmount(), Math.max(0, capacity - carriedCount()));
    if (taking > 0) {
      ItemStack held = stack.clone();
      held.setAmount(taking);
      cargo.add(held);
    }
    if (taking == stack.getAmount()) {
      return Optional.empty();
    }
    ItemStack rest = stack.clone();
    rest.setAmount(stack.getAmount() - taking);
    return Optional.of(rest);
  }

  private int take(Predicate<ItemStack> matches, int amount) {
    int left = amount;
    for (int i = 0; i < cargo.size() && left > 0; i++) {
      ItemStack held = cargo.get(i);
      if (!matches.test(held.clone())) {
        continue;
      }
      int removing = Math.min(left, held.getAmount());
      held.setAmount(held.getAmount() - removing);
      left -= removing;
    }
    cargo.removeIf(item -> item.getAmount() <= 0);
    return amount - left;
  }

  private int deliver(Inventory inventory, ToIntFunction<ItemStack> amount, IntConsumer stackDelivered) {
    int moved = 0;
    for (ItemStack held : List.copyOf(cargo)) {
      int wanted = Math.min(held.getAmount(), amount.applyAsInt(held.clone()));
      if (wanted <= 0) {
        continue;
      }
      ItemStack offered = held.clone();
      offered.setAmount(wanted);
      int left = 0;
      for (ItemStack leftover : inventory.addItem(offered).values()) {
        left += leftover.getAmount();
      }
      held.setAmount(held.getAmount() - (wanted - left));
      moved += wanted - left;
      if (wanted - left > 0) {
        stackDelivered.accept(wanted - left);
      }
    }
    cargo.removeIf(item -> item.getAmount() <= 0);
    return moved;
  }

  private GolemTask task(String type, int x, int y, int z, ItemStack item) {
    return GolemTask.builder(KEY, NodeType.INPUT, type, GolemTarget.block(SorterYard.WORLD, x, y, z)).item(item).build();
  }

  private GolemTask handTask(Player player) {
    Location at = player.getLocation();
    UUID id = player.getUniqueId();
    Entity entity = mock(Entity.class);
    lenient().when(entity.getLocation()).thenReturn(at);
    lenient().when(entity.getWorld()).thenReturn(yard.world);
    lenient().when(entity.getUniqueId()).thenReturn(id);
    return GolemTask.builder(KEY, NodeType.INPUT, SorterRules.HAND, GolemTarget.entity(entity)).item(items.item(Material.COBBLESTONE, 1)).build();
  }

  private List<String> jobs() {
    List<String> lines = new ArrayList<>();
    for (GolemTask task : posted) {
      lines.add(describe(task));
    }
    lines.sort(null);
    return lines;
  }

  private static String describe(GolemTask task) {
    GolemTarget target = task.target();
    String item = task.item().map(stack -> stack.getType().name()).orElse("-");
    return task.type() + " " + target.blockX() + "," + target.blockY() + "," + target.blockZ() + " " + item;
  }
}
