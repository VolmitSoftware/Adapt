package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class CollectionNodeBehaviorTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
  private static final NodeKey KEY = new NodeKey(WORLD, 10, 64, 0, BlockFace.UP);
  private static final double ITEM_HALF_WIDTH = 0.125D;

  private final List<Entity> nearby = new ArrayList<>();
  private final Map<Entity, AtomicReference<Location>> positions = new HashMap<>();
  private final Set<Entity> removed = new HashSet<>();
  private final List<GolemTask> open = new ArrayList<>();
  private final List<GolemTask> posted = new ArrayList<>();
  private final List<GolemTask> withdrawn = new ArrayList<>();
  private final AtomicInteger room = new AtomicInteger(16);
  private final AtomicInteger carried = new AtomicInteger();
  private final List<ItemStack> carriedStacks = new ArrayList<>();
  private final AtomicBoolean reactEnabled = new AtomicBoolean(true);
  private final GolemGathererCore.Config config = new GolemGathererCore.Config();
  private final GatherDeathSites deathSites = new GatherDeathSites();
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<ProtectionEventProbe> probes;
  private World world;
  private GolemGathererCore core;
  private CollectionNodeBehavior behavior;
  private NodeRecord record;

  @BeforeEach
  void setUp() {
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class), anyDouble(), anyDouble())).thenReturn(true);
    PluginManager plugins = mock(PluginManager.class);
    lenient().when(plugins.isPluginEnabled("React")).thenAnswer(invocation -> reactEnabled.get());
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
    probes = mockStatic(ProtectionEventProbe.class);
    probes.when(() -> ProtectionEventProbe.attemptEntityItemPickup(any(LivingEntity.class), any(Item.class), anyInt())).thenReturn(true);
    world = mock(World.class, withSettings().strictness(Strictness.LENIENT));
    when(world.getUID()).thenReturn(WORLD);
    when(world.getNearbyEntities(any(BoundingBox.class), any())).thenAnswer(invocation -> {
      BoundingBox box = invocation.<BoundingBox>getArgument(0).clone().expand(ITEM_HALF_WIDTH);
      Predicate<Entity> filter = invocation.getArgument(1);
      List<Entity> found = new ArrayList<>();
      for (Entity entity : nearby) {
        Location location = positions.get(entity).get();
        if (box.contains(location.getX(), location.getY(), location.getZ()) && filter.test(entity)) {
          found.add(entity);
        }
      }
      return found;
    });
    core = mock(GolemGathererCore.class);
    when(core.config()).thenReturn(config);
    behavior = new CollectionNodeBehavior(core, new GathererWork(core, deathSites));
    record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).radii(2, 1, 2).build();
  }

  @AfterEach
  void close() {
    probes.close();
    bukkit.close();
    scheduling.close();
  }

  @Test
  void itActsForTheGathererCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(core);
  }

  @Test
  void scanningPostsAPickupJobForEachCollectableItemInTheArea() {
    Item inside = item(Material.WHEAT, at(11.5D, 65D, 1.5D));
    Item corner = item(Material.CARROT, at(12.9D, 66.9D, -1.9D));
    item(Material.WHEAT, at(13.1D, 65D, 0.5D));
    item(Material.WHEAT, at(10.5D, 63.9D, 0.5D));
    item(Material.WHEAT, at(10.5D, 65D, 0.5D)).setPickupDelay(10);
    Item thrown = item(Material.WHEAT, at(10.6D, 65D, 0.5D));
    when(thrown.getThrower()).thenReturn(STRANGER);
    Item grave = item(Material.DIAMOND, at(9.5D, 65D, 1.5D));
    config.deathSiteRadius = 1.5D;
    deathSites.record(WORLD, 8.5D, 65D, 1.5D, 1_000L, 300_000L);

    behavior.scan(node(), budget(16));

    assertThat(posted).extracting(task -> task.target().entity()).containsExactlyInAnyOrder(inside.getUniqueId(), corner.getUniqueId());
    assertThat(posted).allSatisfy(task -> {
      assertThat(task.type()).isEqualTo(GathererWork.PICKUP);
      assertThat(task.node()).contains(KEY);
      assertThat(task.nodeType()).contains(NodeType.COLLECTION);
    });
    assertThat(posted).extracting(task -> task.target().entity()).doesNotContain(grave.getUniqueId());
  }

  @Test
  void theNodeFilterDecidesWhichItemsAreCollected() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build();
    item(Material.WHEAT, at(11.5D, 65D, 0.5D));
    Item carrot = item(Material.CARROT, at(10.5D, 65D, 0.5D));

    behavior.scan(node(), budget(16));

    assertThat(posted).extracting(task -> task.target().entity()).containsExactly(carrot.getUniqueId());
  }

  @Test
  void scanningStopsPostingWhenTheNodeHasNoRoomForMoreJobs() {
    item(Material.WHEAT, at(11.5D, 65D, 0.5D));
    item(Material.WHEAT, at(10.5D, 65D, 0.5D));
    item(Material.WHEAT, at(9.5D, 65D, 0.5D));

    behavior.scan(node(), budget(2));

    assertThat(posted).hasSize(2);
  }

  @Test
  void jobsForItemsThatAreGoneOrNoLongerCollectableAreWithdrawn() {
    Item kept = item(Material.WHEAT, at(11.5D, 65D, 0.5D));
    Item delayed = item(Material.WHEAT, at(10.5D, 65D, 0.5D));
    Item gone = item(Material.WHEAT, at(9.5D, 65D, 0.5D));
    GolemTask keptTask = task(kept);
    GolemTask delayedTask = task(delayed);
    GolemTask goneTask = task(gone);
    open.addAll(List.of(keptTask, delayedTask, goneTask));
    gone.remove();
    delayed.setPickupDelay(40);

    behavior.scan(node(), budget(16));

    assertThat(withdrawn).containsExactlyInAnyOrder(delayedTask, goneTask);
  }

  @Test
  void aScanWhoseAreaCrossesIntoAnotherRegionChangesNothing() {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class), anyDouble(), anyDouble())).thenReturn(false);
    Item item = item(Material.WHEAT, at(11.5D, 65D, 0.5D));
    open.add(task(item));

    behavior.scan(node(), budget(16));

    assertThat(posted).isEmpty();
    assertThat(withdrawn).isEmpty();
    verify(world, never()).getNearbyEntities(any(BoundingBox.class), any());
  }

  @Test
  void actingPicksUpTheItemInsideTheNodeArea() {
    Item item = item(Material.WHEAT, at(11.5D, 65D, 0.5D));

    assertThat(behavior.act(golem(item), task(item))).isEqualTo(GolemActResult.DONE);

    assertThat(carried.get()).isEqualTo(1);
    assertThat(removed).contains(item);
  }

  @Test
  void actingFailsForAnItemThatLeftTheAreaOrThatTheFilterRejects() {
    Item drifted = item(Material.WHEAT, at(11.5D, 65D, 0.5D));
    positions.get(drifted).set(at(15.5D, 65D, 0.5D));
    assertThat(behavior.act(golem(drifted), task(drifted))).isEqualTo(GolemActResult.FAILED);

    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build();
    Item wheat = item(Material.WHEAT, at(11.5D, 65D, 0.5D));
    assertThat(behavior.act(golem(wheat), task(wheat))).isEqualTo(GolemActResult.FAILED);
    assertThat(carried.get()).isZero();
  }

  @Test
  void scanningPostsAJobForASuperStackWhenTheFilterAcceptsAnythingInsideIt() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build();
    Item mixed = item(ReactBundles.superStack(GolemTestItems.stack(Material.WHEAT, 2), GolemTestItems.stack(Material.CARROT, 3)), at(11.5D, 65D, 0.5D));
    item(ReactBundles.superStack(GolemTestItems.stack(Material.WHEAT, 2)), at(10.5D, 65D, 0.5D));
    item(ReactBundles.superStack(), at(9.5D, 65D, 0.5D));

    behavior.scan(node(), budget(16));

    assertThat(posted).extracting(task -> task.target().entity()).containsExactly(mixed.getUniqueId());
  }

  @Test
  void aSuperStackIsOnlyAJobWhileReactIsEnabled() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build();
    item(ReactBundles.superStack(GolemTestItems.stack(Material.CARROT, 3)), at(11.5D, 65D, 0.5D));
    reactEnabled.set(false);

    behavior.scan(node(), budget(16));

    assertThat(posted).isEmpty();
  }

  @Test
  void actingUnpacksASuperStackInsideTheNodeArea() {
    Item bundle = item(ReactBundles.superStack(GolemTestItems.stack(Material.WHEAT, 2), GolemTestItems.stack(Material.CARROT, 3)), at(11.5D, 65D, 0.5D));

    assertThat(behavior.act(golem(bundle), task(bundle))).isEqualTo(GolemActResult.DONE);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx2", "CARROTx3");
    assertThat(carriedStacks).noneMatch(stack -> stack.getType() == Material.BUNDLE);
    assertThat(removed).contains(bundle);
  }

  @Test
  void actingTakesOnlyWhatTheFilterAcceptsFromASuperStackAndKeepsTheRest() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build();
    Item bundle = item(ReactBundles.superStack(GolemTestItems.stack(Material.WHEAT, 2), GolemTestItems.stack(Material.CARROT, 3)), at(11.5D, 65D, 0.5D));
    Item wheatOnly = item(ReactBundles.superStack(GolemTestItems.stack(Material.WHEAT, 2)), at(10.5D, 65D, 0.5D));

    assertThat(behavior.act(golem(bundle), task(bundle))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(wheatOnly), task(wheatOnly))).isEqualTo(GolemActResult.FAILED);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("CARROTx3");
    assertThat(removed).doesNotContain(bundle, wheatOnly);
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle.getItemStack()))).containsExactly("WHEATx2");
    assertThat(ReactBundles.lore(bundle.getItemStack())).containsExactly(ReactBundles.MARKER);
  }

  private GolemTask task(Item item) {
    return GolemTask.builder(KEY, NodeType.COLLECTION, GathererWork.PICKUP, GolemTarget.entity(item)).build();
  }

  private NodeContext node() {
    NodeContext context = mock(NodeContext.class, withSettings().strictness(Strictness.LENIENT));
    when(context.world()).thenReturn(world);
    when(context.key()).thenReturn(KEY);
    when(context.record()).thenAnswer(invocation -> record);
    when(context.owner()).thenReturn(OWNER);
    when(context.nowMillis()).thenReturn(2_000L);
    when(context.task(anyString(), any(GolemTarget.class)))
        .thenAnswer(invocation -> GolemTask.builder(KEY, NodeType.COLLECTION, invocation.getArgument(0), invocation.getArgument(1)));
    when(context.post(any(GolemTask.class))).thenAnswer(invocation -> {
      posted.add(invocation.getArgument(0));
      room.decrementAndGet();
      return true;
    });
    when(context.tasks()).thenAnswer(invocation -> List.copyOf(open));
    when(context.withdraw(any(GolemTask.class))).thenAnswer(invocation -> {
      withdrawn.add(invocation.getArgument(0));
      return true;
    });
    return context;
  }

  private ScanBudget budget(int taskRoom) {
    room.set(taskRoom);
    ScanBudget budget = mock(ScanBudget.class, withSettings().strictness(Strictness.LENIENT));
    when(budget.hasTaskRoom()).thenAnswer(invocation -> room.get() > 0);
    return budget;
  }

  private GolemContext golem(Item item) {
    IronGolem body = mock(IronGolem.class, withSettings().strictness(Strictness.LENIENT));
    when(body.getLocation()).thenReturn(at(11.5D, 65D, 1.5D));
    Player owner = mock(Player.class, withSettings().strictness(Strictness.LENIENT));
    when(owner.getUniqueId()).thenReturn(OWNER);
    when(owner.getWorld()).thenReturn(world);
    GolemHandle handle = mock(GolemHandle.class, withSettings().strictness(Strictness.LENIENT));
    when(handle.owner()).thenReturn(OWNER);
    GolemContext context = mock(GolemContext.class, withSettings().strictness(Strictness.LENIENT));
    when(context.handle()).thenReturn(handle);
    when(context.golem()).thenReturn(body);
    when(context.owner()).thenReturn(owner);
    when(context.world()).thenReturn(world);
    when(context.core()).thenReturn(GolemCoreType.GATHERER);
    when(context.nowMillis()).thenReturn(2_000L);
    when(context.node()).thenAnswer(invocation -> Optional.of(record));
    when(context.targetEntity()).thenAnswer(invocation -> Optional.<Entity>of(item).filter(entity -> !removed.contains(entity)));
    when(context.inWorkRadius(any(Location.class))).thenReturn(true);
    when(context.canInteract(any(Location.class))).thenReturn(true);
    when(context.canBreak(any(Location.class))).thenReturn(true);
    when(context.room()).thenAnswer(invocation -> 64 - carried.get());
    when(context.carry(any(ItemStack.class))).thenAnswer(invocation -> {
      ItemStack stack = invocation.getArgument(0);
      carriedStacks.add(stack);
      carried.addAndGet(stack.getAmount());
      return Optional.empty();
    });
    return context;
  }

  private Item item(Material type, Location location) {
    return item(GolemTestItems.stack(type, 1), location);
  }

  private Item item(ItemStack held, Location location) {
    Item item = mock(Item.class, withSettings().strictness(Strictness.LENIENT));
    AtomicReference<Location> where = new AtomicReference<>(location);
    AtomicReference<ItemStack> stack = new AtomicReference<>(held);
    AtomicInteger delay = new AtomicInteger();
    UUID id = UUID.randomUUID();
    positions.put(item, where);
    when(item.getUniqueId()).thenReturn(id);
    when(item.getWorld()).thenReturn(world);
    when(item.getLocation()).thenAnswer(invocation -> where.get().clone());
    when(item.getItemStack()).thenAnswer(invocation -> stack.get());
    doAnswer(invocation -> {
      stack.set(invocation.getArgument(0));
      return null;
    }).when(item).setItemStack(any());
    when(item.getPickupDelay()).thenAnswer(invocation -> delay.get());
    doAnswer(invocation -> {
      delay.set(invocation.getArgument(0));
      return null;
    }).when(item).setPickupDelay(anyInt());
    when(item.canPlayerPickup()).thenReturn(true);
    when(item.canMobPickup()).thenReturn(true);
    when(item.isValid()).thenAnswer(invocation -> !removed.contains(item));
    doAnswer(invocation -> {
      removed.add(item);
      nearby.remove(item);
      return null;
    }).when(item).remove();
    nearby.add(item);
    return item;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x, y, z);
  }
}
