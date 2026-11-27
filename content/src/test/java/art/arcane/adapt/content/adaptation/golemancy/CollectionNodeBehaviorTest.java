package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.engine.integration.ReactSuperStackPort;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.content.golem.GolemActResult;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeContext;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.ScanBudget;
import art.arcane.adapt.engine.protection.ProtectionProbes;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class CollectionNodeBehaviorTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
  private static final NodeKey KEY = new NodeKey(WORLD, 10, 64, 0, BlockFace.UP);
  private static final double ITEM_HALF_WIDTH = 0.125D;

  private final List<EntityRef> nearby = new ArrayList<>();
  private final Map<EntityRef, AtomicReference<Loc>> positions = new HashMap<>();
  private final Set<EntityRef> removed = new HashSet<>();
  private final List<GolemTask> open = new ArrayList<>();
  private final List<GolemTask> posted = new ArrayList<>();
  private final List<GolemTask> withdrawn = new ArrayList<>();
  private final AtomicInteger room = new AtomicInteger(16);
  private final AtomicInteger carried = new AtomicInteger();
  private final List<ItemRef> carriedStacks = new ArrayList<>();
  private final AtomicBoolean reactEnabled = new AtomicBoolean(true);
  private final GolemGathererCore.Config config = new GolemGathererCore.Config();
  private final GatherDeathSites deathSites = new GatherDeathSites();
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<ProtectionProbes> probes;
  private WorldRef world;
  private GolemGathererCore core;
  private CollectionNodeBehavior behavior;
  private NodeRecord record;

  @BeforeEach
  void setUp() {
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.owns(any(Loc.class))).thenReturn(true);
    when(adaptScheduler.ownsArea(any(Loc.class), anyDouble(), anyDouble())).thenReturn(true);
    AdaptPorts.install(ReactSuperStackPort.class, ReactBundles.port(reactEnabled::get));
    probes = mockStatic(ProtectionProbes.class);
    probes.when(() -> ProtectionProbes.attemptEntityItemPickup(any(LivingRef.class), any(ItemEntityRef.class), anyInt())).thenReturn(true);
    world = mock(WorldRef.class, withSettings().strictness(Strictness.LENIENT));
    when(world.uid()).thenReturn(WORLD);
    when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    when(world.entitiesIn(any(Box.class), any())).thenAnswer(invocation -> {
      Box box = invocation.<Box>getArgument(0).expand(ITEM_HALF_WIDTH);
      Predicate<EntityRef> filter = invocation.getArgument(1);
      List<EntityRef> found = new ArrayList<>();
      for (EntityRef entity : nearby) {
        Loc location = positions.get(entity).get();
        if (box.contains(location.position()) && filter.test(entity)) {
          found.add(entity);
        }
      }
      return found;
    });
    core = mock(GolemGathererCore.class);
    when(core.config()).thenReturn(config);
    behavior = new CollectionNodeBehavior(core, new GathererWork(core, deathSites));
    record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(2, 1, 2)).build();
  }

  @AfterEach
  void close() {
    probes.close();
    AdaptPorts.uninstall(ReactSuperStackPort.class);
    scheduling.close();
  }

  @Test
  void anEmptyQueryReportsItsBackoffAndADeferredScanDoesNotQueryTheWorld() {
    NodeContext node = node();
    behavior.scan(node, budget(16));
    verify(node).queryResult(false);
    when(node.queryDue()).thenReturn(false);
    clearInvocations(world);

    behavior.scan(node, budget(16));

    verify(world, never()).entitiesIn(any(Box.class), any());
  }

  @Test
  void itActsForTheGathererCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(core);
  }

  @Test
  void scanningPostsAPickupJobForEachCollectableItemInTheArea() {
    ItemEntityRef inside = item(Id.minecraft("wheat"), at(11.5D, 65D, 1.5D));
    ItemEntityRef corner = item(Id.minecraft("carrot"), at(12.9D, 66.9D, -1.9D));
    item(Id.minecraft("wheat"), at(13.1D, 65D, 0.5D));
    item(Id.minecraft("wheat"), at(10.5D, 63.9D, 0.5D));
    item(Id.minecraft("wheat"), at(10.5D, 65D, 0.5D)).setPickupDelay(10);
    ItemEntityRef thrown = item(Id.minecraft("wheat"), at(10.6D, 65D, 0.5D));
    when(thrown.thrower()).thenReturn(STRANGER);
    ItemEntityRef grave = item(Id.minecraft("diamond"), at(9.5D, 65D, 1.5D));
    config.deathSiteRadius = 1.5D;
    deathSites.record(WORLD, 8.5D, 65D, 1.5D, 1_000L, 300_000L);

    behavior.scan(node(), budget(16));

    assertThat(posted).extracting(task -> task.target().entity()).containsExactlyInAnyOrder(inside.uuid(), corner.uuid());
    assertThat(posted).allSatisfy(task -> {
      assertThat(task.type()).isEqualTo(GathererWork.PICKUP);
      assertThat(task.node()).contains(KEY);
      assertThat(task.nodeType()).contains(NodeType.COLLECTION);
    });
    assertThat(posted).extracting(task -> task.target().entity()).doesNotContain(grave.uuid());
  }

  @Test
  void theNodeFilterDecidesWhichItemsAreCollected() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Id.minecraft("carrot"), 1))).build();
    item(Id.minecraft("wheat"), at(11.5D, 65D, 0.5D));
    ItemEntityRef carrot = item(Id.minecraft("carrot"), at(10.5D, 65D, 0.5D));

    behavior.scan(node(), budget(16));

    assertThat(posted).extracting(task -> task.target().entity()).containsExactly(carrot.uuid());
  }

  @Test
  void scanningStopsPostingWhenTheNodeHasNoRoomForMoreJobs() {
    item(Id.minecraft("wheat"), at(11.5D, 65D, 0.5D));
    item(Id.minecraft("wheat"), at(10.5D, 65D, 0.5D));
    item(Id.minecraft("wheat"), at(9.5D, 65D, 0.5D));

    behavior.scan(node(), budget(2));

    assertThat(posted).hasSize(2);
  }

  @Test
  void jobsForItemsThatAreGoneOrNoLongerCollectableAreWithdrawn() {
    ItemEntityRef kept = item(Id.minecraft("wheat"), at(11.5D, 65D, 0.5D));
    ItemEntityRef delayed = item(Id.minecraft("wheat"), at(10.5D, 65D, 0.5D));
    ItemEntityRef gone = item(Id.minecraft("wheat"), at(9.5D, 65D, 0.5D));
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
    when(adaptScheduler.isRegionized()).thenReturn(true);
    when(adaptScheduler.ownsArea(any(Loc.class), anyDouble(), anyDouble())).thenReturn(false);
    ItemEntityRef item = item(Id.minecraft("wheat"), at(11.5D, 65D, 0.5D));
    open.add(task(item));

    behavior.scan(node(), budget(16));

    assertThat(posted).isEmpty();
    assertThat(withdrawn).isEmpty();
    verify(world, never()).entitiesIn(any(Box.class), any());
  }

  @Test
  void actingPicksUpTheItemInsideTheNodeArea() {
    ItemEntityRef item = item(Id.minecraft("wheat"), at(11.5D, 65D, 0.5D));

    assertThat(behavior.act(golem(item), task(item))).isEqualTo(GolemActResult.DONE);

    assertThat(carried.get()).isEqualTo(1);
    assertThat(removed).contains(item);
  }

  @Test
  void actingFailsForAnItemThatLeftTheAreaOrThatTheFilterRejects() {
    ItemEntityRef drifted = item(Id.minecraft("wheat"), at(11.5D, 65D, 0.5D));
    positions.get(drifted).set(at(15.5D, 65D, 0.5D));
    assertThat(behavior.act(golem(drifted), task(drifted))).isEqualTo(GolemActResult.FAILED);

    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Id.minecraft("carrot"), 1))).build();
    ItemEntityRef wheat = item(Id.minecraft("wheat"), at(11.5D, 65D, 0.5D));
    assertThat(behavior.act(golem(wheat), task(wheat))).isEqualTo(GolemActResult.FAILED);
    assertThat(carried.get()).isZero();
  }

  @Test
  void scanningPostsAJobForASuperStackWhenTheFilterAcceptsAnythingInsideIt() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Id.minecraft("carrot"), 1))).build();
    ItemEntityRef mixed = item(ReactBundles.superStack(GolemTestItems.stack(Id.minecraft("wheat"), 2), GolemTestItems.stack(Id.minecraft("carrot"), 3)), at(11.5D, 65D, 0.5D));
    item(ReactBundles.superStack(GolemTestItems.stack(Id.minecraft("wheat"), 2)), at(10.5D, 65D, 0.5D));
    item(ReactBundles.superStack(), at(9.5D, 65D, 0.5D));

    behavior.scan(node(), budget(16));

    assertThat(posted).extracting(task -> task.target().entity()).containsExactly(mixed.uuid());
  }

  @Test
  void aSuperStackIsOnlyAJobWhileReactIsEnabled() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Id.minecraft("carrot"), 1))).build();
    item(ReactBundles.superStack(GolemTestItems.stack(Id.minecraft("carrot"), 3)), at(11.5D, 65D, 0.5D));
    reactEnabled.set(false);

    behavior.scan(node(), budget(16));

    assertThat(posted).isEmpty();
  }

  @Test
  void actingUnpacksASuperStackInsideTheNodeArea() {
    ItemEntityRef bundle = item(ReactBundles.superStack(GolemTestItems.stack(Id.minecraft("wheat"), 2), GolemTestItems.stack(Id.minecraft("carrot"), 3)), at(11.5D, 65D, 0.5D));

    assertThat(behavior.act(golem(bundle), task(bundle))).isEqualTo(GolemActResult.DONE);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx2", "CARROTx3");
    assertThat(carriedStacks).noneMatch(stack -> stack.type().equals(Id.minecraft("bundle")));
    assertThat(removed).contains(bundle);
  }

  @Test
  void actingTakesOnlyWhatTheFilterAcceptsFromASuperStackAndKeepsTheRest() {
    record = record.toBuilder().filterItems(List.of(GolemTestItems.stack(Id.minecraft("carrot"), 1))).build();
    ItemEntityRef bundle = item(ReactBundles.superStack(GolemTestItems.stack(Id.minecraft("wheat"), 2), GolemTestItems.stack(Id.minecraft("carrot"), 3)), at(11.5D, 65D, 0.5D));
    ItemEntityRef wheatOnly = item(ReactBundles.superStack(GolemTestItems.stack(Id.minecraft("wheat"), 2)), at(10.5D, 65D, 0.5D));

    assertThat(behavior.act(golem(bundle), task(bundle))).isEqualTo(GolemActResult.DONE);
    assertThat(behavior.act(golem(wheatOnly), task(wheatOnly))).isEqualTo(GolemActResult.FAILED);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("CARROTx3");
    assertThat(removed).doesNotContain(bundle, wheatOnly);
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle.item()))).containsExactly("WHEATx2");
    assertThat(ReactBundles.lore(bundle.item())).containsExactly(ReactBundles.MARKER);
  }

  private GolemTask task(ItemEntityRef item) {
    return GolemTask.builder(KEY, NodeType.COLLECTION, GathererWork.PICKUP, GolemTarget.entity(item)).build();
  }

  private NodeContext node() {
    NodeContext context = mock(NodeContext.class, withSettings().strictness(Strictness.LENIENT));
    when(context.world()).thenReturn(world);
    when(context.queryDue()).thenReturn(true);
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

  private GolemContext golem(ItemEntityRef item) {
    MobRef body = mock(MobRef.class, withSettings().strictness(Strictness.LENIENT));
    when(body.location()).thenReturn(at(11.5D, 65D, 1.5D));
    PlayerRef owner = mock(PlayerRef.class, withSettings().strictness(Strictness.LENIENT));
    when(owner.uuid()).thenReturn(OWNER);
    when(owner.world()).thenReturn(world);
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
    when(context.targetEntity()).thenAnswer(invocation -> Optional.<EntityRef>of(item).filter(entity -> !removed.contains(entity)));
    when(context.inWorkRadius(any(Loc.class))).thenReturn(true);
    when(context.canInteract(any(Loc.class))).thenReturn(true);
    when(context.canBreak(any(Loc.class))).thenReturn(true);
    when(context.room()).thenAnswer(invocation -> 64 - carried.get());
    when(context.carry(any(ItemRef.class))).thenAnswer(invocation -> {
      ItemRef stack = invocation.getArgument(0);
      carriedStacks.add(stack);
      carried.addAndGet(stack.amount());
      return Optional.empty();
    });
    return context;
  }

  private ItemEntityRef item(Id type, Loc location) {
    return item(GolemTestItems.stack(type, 1), location);
  }

  private ItemEntityRef item(ItemRef held, Loc location) {
    ItemEntityRef item = mock(ItemEntityRef.class, withSettings().strictness(Strictness.LENIENT));
    AtomicReference<Loc> where = new AtomicReference<>(location);
    AtomicReference<ItemRef> stack = new AtomicReference<>(held);
    AtomicInteger delay = new AtomicInteger();
    UUID id = UUID.randomUUID();
    positions.put(item, where);
    when(item.uuid()).thenReturn(id);
    when(item.world()).thenReturn(world);
    when(item.location()).thenAnswer(invocation -> where.get());
    when(item.item()).thenAnswer(invocation -> stack.get());
    doAnswer(invocation -> {
      stack.set(invocation.getArgument(0));
      return null;
    }).when(item).setItem(any());
    when(item.pickupDelay()).thenAnswer(invocation -> delay.get());
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

  private Loc at(double x, double y, double z) {
    return new Loc(world, x, y, z);
  }
}
