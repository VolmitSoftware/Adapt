package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRunes;
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
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class GathererWorkTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000d3");
  private static final long FIVE_MINUTES = 300_000L;

  private final List<Entity> nearby = new ArrayList<>();
  private final Map<Entity, AtomicReference<Location>> positions = new HashMap<>();
  private final Set<Entity> removed = new HashSet<>();
  private final Map<Entity, Vector> velocities = new HashMap<>();
  private final Set<UUID> skipped = new HashSet<>();
  private final List<NodeIndex.Entry> nodes = new ArrayList<>();
  private final List<Double> workXp = new ArrayList<>();
  private final List<Integer> probedRemaining = new ArrayList<>();
  private final AtomicLong clock = new AtomicLong(100_000L);
  private final AtomicInteger level = new AtomicInteger(1);
  private final AtomicInteger capacity = new AtomicInteger(64);
  private final AtomicInteger carried = new AtomicInteger();
  private final AtomicReference<Double> essence = new AtomicReference<>(0D);
  private final AtomicReference<Entity> target = new AtomicReference<>();
  private final AtomicBoolean probe = new AtomicBoolean(true);
  private final AtomicReference<Predicate<Location>> interact = new AtomicReference<>(location -> true);
  private final AtomicReference<Predicate<Location>> breakable = new AtomicReference<>(location -> true);
  private final List<Entity> probedEntities = new ArrayList<>();
  private final List<ItemStack> carriedStacks = new ArrayList<>();
  private final List<ItemStack> dropped = new ArrayList<>();
  private final AtomicBoolean reactEnabled = new AtomicBoolean(true);
  private final List<Item> probedItems = new ArrayList<>();
  private final AtomicReference<Double> workRadius = new AtomicReference<>(24D);
  private final AtomicReference<GolemState.Anchor> home = new AtomicReference<>(GolemState.Anchor.of(WORLD, 0, 64, 0));
  private final GolemGathererCore.Config config = new GolemGathererCore.Config();
  private final GatherDeathSites deathSites = new GatherDeathSites();
  private final List<GolemRunes.Augments> augments = new ArrayList<>();
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<ProtectionEventProbe> probes;
  private World world;
  private World nether;
  private Player owner;
  private GolemGathererCore core;
  private GathererWork work;
  private IronGolem body;
  private GolemHandle handle;
  private GolemContext golem;
  private NodeIndex index;

  @BeforeEach
  void setUp() {
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    PluginManager plugins = mock(PluginManager.class);
    lenient().when(plugins.isPluginEnabled("React")).thenAnswer(invocation -> reactEnabled.get());
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
    world = world(WORLD);
    nether = world(UUID.fromString("00000000-0000-0000-0000-0000000000d4"));
    index = mock(NodeIndex.class);
    lenient().when(index.near(eq(WORLD), anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> List.copyOf(nodes));
    NodeService service = mock(NodeService.class);
    lenient().when(service.index()).thenReturn(index);
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    probes = mockStatic(ProtectionEventProbe.class);
    probes.when(() -> ProtectionEventProbe.attemptEntityItemPickup(any(LivingEntity.class), any(Item.class), anyInt())).thenAnswer(invocation -> {
      probedEntities.add(invocation.getArgument(0));
      probedItems.add(invocation.getArgument(1));
      probedRemaining.add(invocation.getArgument(2));
      return probe.get();
    });
    core = mock(GolemGathererCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.ownerLevel(OWNER)).thenAnswer(invocation -> level.get());
    lenient().when(core.magnets(OWNER)).thenReturn(true);
    lenient().when(core.absorbsOrbs(OWNER)).thenReturn(true);
    work = new GathererWork(core, deathSites);
    owner = mock(Player.class, withSettings().strictness(Strictness.LENIENT));
    when(owner.getUniqueId()).thenReturn(OWNER);
    when(owner.getWorld()).thenReturn(world);
    body = mock(IronGolem.class, withSettings().strictness(Strictness.LENIENT));
    AtomicReference<Location> golemAt = new AtomicReference<>(at(0.5D, 64D, 0.5D));
    positions.put(body, golemAt);
    when(body.getLocation()).thenAnswer(invocation -> golemAt.get().clone());
    when(body.getWorld()).thenReturn(world);
    handle = handle();
    golem = context();
  }

  @AfterEach
  void close() {
    for (GolemRunes.Augments registered : augments) {
      GolemRunes.unregister(registered);
    }
    probes.close();
    runtime.close();
    bukkit.close();
    scheduling.close();
  }

  @Test
  void itActsForTheGathererCoreAdaptation() {
    assertThat(work.policy()).isSameAs(core);
  }

  @Test
  void withoutACollectionNodeItSeeksTheNearestCollectableItemWithinThePickupRadiusOfHome() {
    item(Material.WHEAT, 4, at(3.4D, 64D, 0.5D));
    Item nearest = item(Material.WHEAT, 4, at(2.5D, 64D, 0.5D));
    item(Material.WHEAT, 4, at(5.5D, 64D, 0.5D));

    Optional<GolemTask> found = work.seek(golem);

    assertThat(found).hasValueSatisfying(task -> {
      assertThat(task.type()).isEqualTo(GathererWork.PICKUP);
      assertThat(task.node()).isEmpty();
      assertThat(task.target().kind()).isEqualTo(GolemTarget.Kind.ITEM);
      assertThat(task.target().entity()).isEqualTo(nearest.getUniqueId());
    });
  }

  @Test
  void seekingPassesOverItemsTheGolemMayNotTakeYet() {
    item(Material.WHEAT, 1, at(1.5D, 64D, 0.5D)).setPickupDelay(10);
    Item thrown = item(Material.WHEAT, 1, at(1.6D, 64D, 0.5D));
    when(thrown.getThrower()).thenReturn(STRANGER);
    when(thrown.getTicksLived()).thenReturn(100);
    Item owned = item(Material.WHEAT, 1, at(1.7D, 64D, 0.5D));
    when(owned.getOwner()).thenReturn(STRANGER);
    Item display = item(Material.WHEAT, 1, at(1.8D, 64D, 0.5D));
    when(display.canPlayerPickup()).thenReturn(false);
    Item shop = item(Material.WHEAT, 1, at(1.85D, 64D, 0.5D));
    when(shop.hasMetadata("shopitem")).thenReturn(true);
    Item reserved = item(Material.WHEAT, 1, at(1.9D, 64D, 0.5D));
    skipped.add(reserved.getUniqueId());
    Item old = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    when(old.getThrower()).thenReturn(STRANGER);
    when(old.getTicksLived()).thenReturn(600);

    assertThat(work.seek(golem)).hasValueSatisfying(task -> assertThat(task.target().entity()).isEqualTo(old.getUniqueId()));
  }

  @Test
  void itemsAroundARecentPlayerDeathAreLeftAlone() {
    item(Material.DIAMOND, 1, at(2.5D, 64D, 0.5D));
    deathSites.record(WORLD, 2.5D, 64D, 2.5D, clock.get(), FIVE_MINUTES);

    assertThat(work.seek(golem)).isEmpty();

    clock.addAndGet(FIVE_MINUTES);
    assertThat(work.seek(golem)).isPresent();
  }

  @Test
  void thePickupRadiusGrowsWithLevelAndTheTideRune() {
    item(Material.WHEAT, 1, at(7.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).isEmpty();

    level.set(3);
    clock.addAndGet(GathererWork.AREA_CACHE_MILLIS);
    assertThat(work.seek(golem)).isPresent();

    level.set(1);
    tide(4D);
    clock.addAndGet(GathererWork.AREA_CACHE_MILLIS);
    assertThat(work.seek(golem)).isPresent();
  }

  @Test
  void itemsBeyondTheWorkRadiusOfHomeAreIgnored() {
    level.set(5);
    workRadius.set(6D);
    item(Material.WHEAT, 1, at(9.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).isEmpty();
  }

  @Test
  void aGolemWithNoRoomSeeksNoItems() {
    item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    carried.set(64);

    assertThat(work.seek(golem)).isEmpty();
  }

  @Test
  void gatheringGoesOnWhileTheOwnerIsInAnotherWorld() {
    Item item = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    when(owner.getWorld()).thenReturn(nether);
    target.set(item);

    assertThat(work.seek(golem)).isPresent();
    assertThat(work.act(golem, pickup(item))).isEqualTo(GolemActResult.DONE);
  }

  @Test
  void itemsMobsMayNotPickUpAreSkipped() {
    Item locked = item(Material.WHEAT, 1, at(1.5D, 64D, 0.5D));
    when(locked.canMobPickup()).thenReturn(false);
    Item open = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).hasValueSatisfying(task -> assertThat(task.target().entity()).isEqualTo(open.getUniqueId()));
    target.set(locked);
    assertThat(work.act(golem, pickup(locked))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void itemsAnotherPlayerOwnsAreNeverTakenButThrownItemsWaitOnlyThirtySeconds() {
    Item owned = item(Material.WHEAT, 1, at(1.5D, 64D, 0.5D));
    when(owned.getOwner()).thenReturn(STRANGER);
    when(owned.getTicksLived()).thenReturn(72_000);
    Item thrown = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    when(thrown.getThrower()).thenReturn(STRANGER);
    when(thrown.getTicksLived()).thenReturn(600);

    assertThat(work.seek(golem)).hasValueSatisfying(task -> assertThat(task.target().entity()).isEqualTo(thrown.getUniqueId()));
  }

  @Test
  void withACollectionNodeItemsComeOnlyFromTheBoard() {
    item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    item(Material.WHEAT, 1, at(10.5D, 65D, 0.5D));
    collectionNode(new NodeKey(WORLD, 10, 64, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).build());

    assertThat(work.seek(golem)).isEmpty();
    verify(world, never()).getNearbyEntities(any(BoundingBox.class), any());
  }

  @Test
  void nodesOfOtherOwnersOtherChannelsOrOtherTypesLeaveTheGolemGatheringAroundHome() {
    item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    collectionNode(new NodeKey(WORLD, 10, 64, 0, BlockFace.UP), NodeRecord.builder(STRANGER, NodeType.COLLECTION, BlockFace.UP).build());
    collectionNode(new NodeKey(WORLD, 11, 64, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).channel(DyeColor.RED).build());
    collectionNode(new NodeKey(WORLD, 12, 64, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).build());

    assertThat(work.seek(golem)).isPresent();
  }

  @Test
  void essenceOrbsAreSoughtFromLevelTwo() {
    ExperienceOrb orb = orb(7, 1, at(2.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).isEmpty();

    level.set(2);
    clock.addAndGet(GathererWork.AREA_CACHE_MILLIS);
    assertThat(work.seek(golem)).hasValueSatisfying(task -> {
      assertThat(task.type()).isEqualTo(GathererWork.ABSORB);
      assertThat(task.target().entity()).isEqualTo(orb.getUniqueId());
    });
  }

  @Test
  void aFullEssenceStoreSeeksNoOrbsUntilTheCapGrows() {
    level.set(2);
    essence.set(500D);
    orb(7, 1, at(2.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).isEmpty();

    level.set(3);
    clock.addAndGet(GathererWork.AREA_CACHE_MILLIS);
    assertThat(work.seek(golem)).isPresent();
  }

  @Test
  void theNearestOfItemsAndOrbsComesFirst() {
    level.set(2);
    ExperienceOrb orb = orb(7, 1, at(1.5D, 64D, 0.5D));
    item(Material.WHEAT, 1, at(3.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).hasValueSatisfying(task -> assertThat(task.target().entity()).isEqualTo(orb.getUniqueId()));
  }

  @Test
  void withACollectionNodeOrbsAreSoughtInsideItsArea() {
    level.set(2);
    orb(7, 1, at(2.5D, 64D, 0.5D));
    ExperienceOrb inside = orb(7, 1, at(10.5D, 65D, 0.5D));
    collectionNode(new NodeKey(WORLD, 10, 64, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build());

    assertThat(work.seek(golem)).hasValueSatisfying(task -> assertThat(task.target().entity()).isEqualTo(inside.getUniqueId()));
  }

  @Test
  void theAreaIsCachedBrieflyAndRefreshedWhenHomeMoves() {
    work.seek(golem);
    work.seek(golem);
    verify(index, times(1)).near(eq(WORLD), anyDouble(), anyDouble(), anyDouble(), anyDouble());

    home.set(GolemState.Anchor.of(WORLD, 1, 64, 0));
    work.seek(golem);
    verify(index, times(2)).near(eq(WORLD), anyDouble(), anyDouble(), anyDouble(), anyDouble());

    clock.addAndGet(GathererWork.AREA_CACHE_MILLIS);
    work.seek(golem);
    verify(index, times(3)).near(eq(WORLD), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    assertThat(work.pending()).isTrue();

    work.sweep(clock.get() + GathererWork.AREA_CACHE_MILLIS);
    assertThat(work.pending()).isFalse();
  }

  @Test
  void pickingUpCarriesTheWholeStackRemovesTheItemAndPaysWorkXp() {
    Item item = item(Material.WHEAT, 16, at(2.5D, 64D, 0.5D));
    target.set(item);

    assertThat(work.act(golem, pickup(item))).isEqualTo(GolemActResult.DONE);

    assertThat(carried.get()).isEqualTo(16);
    assertThat(removed).contains(item);
    assertThat(probedRemaining).containsExactly(0);
    assertThat(probedEntities).containsExactly(body);
    probes.verify(() -> ProtectionEventProbe.attemptItemPickup(any(), any(), anyInt()), never());
    probes.verify(() -> ProtectionEventProbe.attemptBlockDropPickup(any(), any(), anyInt(), any()), never());
    assertThat(workXp).containsExactly(0.5D);
  }

  @Test
  void aGolemWithLittleRoomTakesPartOfTheStackAndLeavesTheRest() {
    capacity.set(10);
    Item item = item(Material.WHEAT, 16, at(2.5D, 64D, 0.5D));
    target.set(item);

    assertThat(work.act(golem, pickup(item))).isEqualTo(GolemActResult.DONE);

    assertThat(carried.get()).isEqualTo(10);
    assertThat(removed).doesNotContain(item);
    assertThat(item.getItemStack().getAmount()).isEqualTo(6);
    assertThat(probedRemaining).containsExactly(6);
  }

  @Test
  void theOwnersItemPickupProtectionCanBlockAPickup() {
    probe.set(false);
    Item item = item(Material.WHEAT, 16, at(2.5D, 64D, 0.5D));
    target.set(item);

    assertThat(work.act(golem, pickup(item))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(carried.get()).isZero();
    assertThat(removed).doesNotContain(item);
    assertThat(workXp).isEmpty();
  }

  @Test
  void theGathererCoresProtectionPolicyCanBlockAPickupBeforeAnyPickupEventFires() {
    interact.set(location -> false);
    Item item = item(Material.WHEAT, 16, at(2.5D, 64D, 0.5D));
    target.set(item);

    assertThat(work.act(golem, pickup(item))).isEqualTo(GolemActResult.BLOCKED);

    interact.set(location -> true);
    breakable.set(location -> false);
    assertThat(work.act(golem, pickup(item))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(probedRemaining).isEmpty();
    assertThat(carried.get()).isZero();
    assertThat(removed).doesNotContain(item);
  }

  @Test
  void anItemChangedByAPickupListenerIsLeftAlone() {
    Item item = item(Material.WHEAT, 16, at(2.5D, 64D, 0.5D));
    target.set(item);
    probes.when(() -> ProtectionEventProbe.attemptEntityItemPickup(any(LivingEntity.class), any(Item.class), anyInt())).thenAnswer(invocation -> {
      item.getItemStack().setAmount(3);
      return true;
    });

    assertThat(work.act(golem, pickup(item))).isEqualTo(GolemActResult.FAILED);

    assertThat(carried.get()).isZero();
    assertThat(removed).doesNotContain(item);
  }

  @Test
  void pickingUpFailsForItemsThatAreGoneMovedAwayOrNoLongerCollectable() {
    Item gone = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    assertThat(work.act(golem, pickup(gone))).isEqualTo(GolemActResult.FAILED);

    Item drifted = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    positions.get(drifted).set(at(6.5D, 64D, 0.5D));
    target.set(drifted);
    assertThat(work.act(golem, pickup(drifted))).isEqualTo(GolemActResult.FAILED);

    Item thrown = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    when(thrown.getThrower()).thenReturn(STRANGER);
    target.set(thrown);
    assertThat(work.act(golem, pickup(thrown))).isEqualTo(GolemActResult.FAILED);

    Item fine = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    target.set(fine);
    carried.set(64);
    assertThat(work.act(golem, pickup(fine))).isEqualTo(GolemActResult.FAILED);
    assertThat(work.act(golem, absorbTask(fine))).isEqualTo(GolemActResult.FAILED);
    assertThat(probedRemaining).isEmpty();
  }

  @Test
  void absorbingAnOrbAddsItsExperienceToEssenceAndRemovesIt() {
    level.set(2);
    ExperienceOrb orb = orb(7, 2, at(2.5D, 64D, 0.5D));
    target.set(orb);

    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.DONE);

    assertThat(essence.get()).isEqualTo(14D);
    assertThat(removed).contains(orb);
    assertThat(workXp).isEmpty();
  }

  @Test
  void anOrbHoldingMoreThanTheRoomLeftKeepsTheRest() {
    level.set(2);
    essence.set(495D);
    ExperienceOrb orb = orb(7, 2, at(2.5D, 64D, 0.5D));
    target.set(orb);

    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.DONE);

    assertThat(essence.get()).isEqualTo(500D);
    assertThat(removed).doesNotContain(orb);
    verify(orb).setCount(1);
    verify(orb).setExperience(9);
  }

  @Test
  void protectionCanBlockAbsorbingAndOrbsNeedLevelTwoAndRoom() {
    ExperienceOrb orb = orb(7, 1, at(2.5D, 64D, 0.5D));
    target.set(orb);
    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.FAILED);

    level.set(2);
    clock.addAndGet(GathererWork.AREA_CACHE_MILLIS);
    interact.set(location -> false);
    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.BLOCKED);

    essence.set(500D);
    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.FAILED);
    interact.set(location -> true);
    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.FAILED);
    assertThat(removed).doesNotContain(orb);

    essence.set(0D);
    deathSites.record(WORLD, 2.5D, 64D, 0.5D, clock.get(), FIVE_MINUTES);
    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.FAILED);
    assertThat(essence.get()).isZero();
  }

  @Test
  void absorbingNeedsPermissionToBreakBlocksAtTheOrbsBlock() {
    level.set(2);
    ExperienceOrb orb = orb(7, 1, at(2.5D, 64D, 0.5D));
    target.set(orb);
    List<Location> checked = new ArrayList<>();
    breakable.set(location -> !checked.add(location.clone()));

    assertThat(work.act(golem, absorbTask(orb))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(checked).containsExactly(at(2D, 64D, 0D));
    assertThat(essence.get()).isZero();
    assertThat(removed).doesNotContain(orb);
  }

  @Test
  void theMagnetPullsCollectableItemsWithinThreeBlocksTowardTheGolemFromLevelFour() {
    Item near = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    Item far = item(Material.WHEAT, 1, at(4.0D, 64D, 0.5D));
    Item waiting = item(Material.WHEAT, 1, at(0.5D, 64D, 2.5D));
    waiting.setPickupDelay(20);

    level.set(3);
    work.tick(golem);
    assertThat(velocities).isEmpty();

    level.set(4);
    clock.addAndGet(GathererWork.AREA_CACHE_MILLIS);
    work.tick(golem);

    assertThat(velocities).containsOnlyKeys(near);
    Vector pull = velocities.get(near);
    assertThat(pull.getX()).isNegative();
    assertThat(pull.length()).isCloseTo(GathererWork.PULL_SPEED, offset(1.0E-6));
    assertThat(velocities).doesNotContainKeys(far, waiting);
    assertThat(carried.get()).isZero();
  }

  @Test
  void theMagnetPullsNothingWhileTheOwnerHasItSwitchedOff() {
    when(core.magnets(OWNER)).thenReturn(false);
    Item near = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    level.set(4);

    work.tick(golem);

    assertThat(velocities).isEmpty();
    assertThat(removed).doesNotContain(near);

    when(core.magnets(OWNER)).thenReturn(true);
    work.tick(golem);

    assertThat(velocities).containsOnlyKeys(near);
  }

  @Test
  void orbsAreLeftForTheOwnerWhileTheyHaveAbsorbingSwitchedOff() {
    when(core.absorbsOrbs(OWNER)).thenReturn(false);
    level.set(5);
    ExperienceOrb close = orb(5, 1, at(0.5D, 64D, 1.0D));
    ExperienceOrb farther = orb(7, 1, at(2.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).isEmpty();
    work.tick(golem);
    target.set(farther);
    assertThat(work.act(golem, absorbTask(farther))).isEqualTo(GolemActResult.FAILED);

    assertThat(removed).doesNotContain(close, farther);
    assertThat(essence.get()).isZero();

    when(core.absorbsOrbs(OWNER)).thenReturn(true);
    assertThat(work.act(golem, absorbTask(farther))).isEqualTo(GolemActResult.DONE);
    assertThat(removed).contains(farther);
  }

  @Test
  void theMagnetLeavesItemsAloneWhereTheOwnerMayNotInteractOrWhenTheGolemIsFull() {
    level.set(4);
    item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    interact.set(location -> false);

    work.tick(golem);
    assertThat(velocities).isEmpty();

    interact.set(location -> true);
    carried.set(64);
    work.tick(golem);
    assertThat(velocities).isEmpty();
  }

  @Test
  void theMagnetLeavesItemsAloneWhereTheOwnerMayNotBreakBlocksAtTheItemsBlock() {
    level.set(4);
    Item guarded = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    Item open = item(Material.WHEAT, 1, at(0.5D, 64D, 2.5D));
    breakable.set(location -> location.getX() != 2D || location.getZ() != 0D);

    work.tick(golem);

    assertThat(velocities).containsOnlyKeys(open).doesNotContainKey(guarded);
  }

  @Test
  void levelFivePicksUpItemsWithinOneAndAHalfBlocksAtOnce() {
    level.set(5);
    Item close = item(Material.WHEAT, 8, at(1.5D, 64D, 0.5D));
    Item pulled = item(Material.WHEAT, 8, at(3.0D, 64D, 0.5D));

    work.tick(golem);

    assertThat(removed).contains(close);
    assertThat(carried.get()).isEqualTo(8);
    assertThat(velocities).containsOnlyKeys(pulled);
    assertThat(workXp).containsExactly(0.5D);
  }

  @Test
  void instantPickupTakesAFewItemsPerPulseAndAbsorbsNearbyOrbs() {
    level.set(5);
    for (int i = 0; i < GathererWork.PICKUPS_PER_TICK + 2; i++) {
      item(Material.WHEAT, 1, at(0.5D + i * 0.1D, 64D, 1.5D));
    }
    ExperienceOrb orb = orb(5, 1, at(0.5D, 64D, 1.0D));

    work.tick(golem);

    assertThat(carried.get()).isEqualTo(GathererWork.PICKUPS_PER_TICK);
    assertThat(removed).contains(orb);
    assertThat(essence.get()).isEqualTo(5D);
  }

  @Test
  void aFullGathererWithAFullEssenceStoreLooksForNothingWhileTicking() {
    level.set(5);
    item(Material.WHEAT, 1, at(1.5D, 64D, 0.5D));
    carried.set(64);
    essence.set(1500D);

    work.tick(golem);

    verify(world, never()).getNearbyEntities(any(BoundingBox.class), any());
  }

  @Test
  void theMagnetPullsOnlyWhileTheGolemStandsInItsAreaWhereTheOwnerMayInteract() {
    level.set(4);
    Item item = item(Material.WHEAT, 1, at(9.5D, 64D, 0.5D));
    positions.get(body).set(at(11.0D, 64D, 0.5D));

    work.tick(golem);
    assertThat(velocities).isEmpty();

    positions.get(body).set(at(8.0D, 64D, 0.5D));
    Location standing = at(8D, 64D, 0D);
    interact.set(location -> location.distanceSquared(standing) > 1.0E-6D);
    work.tick(golem);
    assertThat(velocities).isEmpty();

    interact.set(location -> true);
    work.tick(golem);
    assertThat(velocities).containsOnlyKeys(item);
  }

  @Test
  void magnetAndInstantPickupFollowTheCollectionNodeFilter() {
    level.set(5);
    collectionNode(new NodeKey(WORLD, 0, 63, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(3, 1, 3))
        .filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build());
    Item closeWheat = item(Material.WHEAT, 1, at(1.0D, 64D, 0.5D));
    Item closeCarrot = item(Material.CARROT, 1, at(0.5D, 64D, 1.5D));
    Item farWheat = item(Material.WHEAT, 1, at(2.9D, 64D, 0.5D));
    Item farCarrot = item(Material.CARROT, 1, at(0.5D, 64D, 2.9D));

    work.tick(golem);

    assertThat(removed).contains(closeCarrot).doesNotContain(closeWheat, farWheat);
    assertThat(carried.get()).isEqualTo(1);
    assertThat(velocities).containsOnlyKeys(farCarrot);
  }

  @Test
  void instantPickupCountsEveryAttemptAndPassesOverItemsTheGolemSkips() {
    level.set(5);
    probe.set(false);
    Item reserved = item(Material.WHEAT, 1, at(0.5D, 64D, 0.6D));
    skipped.add(reserved.getUniqueId());
    for (int i = 0; i < GathererWork.PICKUPS_PER_TICK + 2; i++) {
      item(Material.WHEAT, 1, at(0.5D + i * 0.1D, 64D, 1.5D));
    }

    work.tick(golem);

    assertThat(probedItems).hasSize(GathererWork.PICKUPS_PER_TICK).doesNotContain(reserved);
    assertThat(carried.get()).isZero();
  }

  @Test
  void levelFourWithAFullLoadLooksForNothingEvenWithEssenceRoom() {
    level.set(4);
    item(Material.WHEAT, 1, at(1.5D, 64D, 0.5D));
    orb(5, 1, at(0.5D, 64D, 1.0D));
    carried.set(64);

    work.tick(golem);

    verify(world, never()).getNearbyEntities(any(BoundingBox.class), any());
  }

  @Test
  void aFullGathererThatOnlyAbsorbsSkipsTheMagnetProtectionQueryAndTheItemSearch() {
    level.set(5);
    item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    ExperienceOrb orb = orb(5, 1, at(0.5D, 64D, 1.0D));
    carried.set(64);
    List<Location> queried = new ArrayList<>();
    interact.set(location -> queried.add(location.clone()));

    work.tick(golem);

    assertThat(removed).contains(orb);
    assertThat(essence.get()).isEqualTo(5D);
    assertThat(queried).containsExactly(at(0D, 64D, 1D));
    verify(world, times(1)).getNearbyEntities(any(BoundingBox.class), any());
  }

  @Test
  void aSwitchedOffCollectionNodePausesGatheringInsteadOfFallingBackToHome() {
    Block anchor = mock(Block.class);
    when(anchor.getWorld()).thenReturn(world);
    when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(anchor);
    collectionNode(new NodeKey(WORLD, 10, 64, 0, BlockFace.UP),
        NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).redstone(RedstoneMode.POWERED).build());
    Item item = item(Material.WHEAT, 1, at(1.0D, 64D, 0.5D));

    assertThat(work.seek(golem)).isEmpty();

    level.set(5);
    work.tick(golem);
    assertThat(removed).doesNotContain(item);
    assertThat(carried.get()).isZero();
  }

  @Test
  void tickingBelowLevelFourLooksForNothing() {
    level.set(3);
    item(Material.WHEAT, 1, at(1.5D, 64D, 0.5D));

    work.tick(golem);

    verify(world, never()).getNearbyEntities(any(BoundingBox.class), any());
    assertThat(carried.get()).isZero();
  }

  @Test
  void anUnknownTaskOrTargetFails() {
    Item item = item(Material.WHEAT, 1, at(2.5D, 64D, 0.5D));
    target.set(item);

    assertThat(work.act(golem, GolemTask.builder("dig", GolemTarget.entity(item)).build())).isEqualTo(GolemActResult.FAILED);
    assertThat(carried.get()).isZero();
  }

  @Test
  void aReactSuperStackIsSoughtAsOneItem() {
    Item bundle = item(ReactBundles.superStack(wheat(4), carrot(3)), at(2.5D, 64D, 0.5D));

    assertThat(work.seek(golem)).hasValueSatisfying(task -> {
      assertThat(task.type()).isEqualTo(GathererWork.PICKUP);
      assertThat(task.target().entity()).isEqualTo(bundle.getUniqueId());
    });
  }

  @Test
  void aSuperStackWithNothingToUnpackIsNeverSoughtOrTaken() {
    Item hollow = item(ReactBundles.superStack(), at(2.5D, 64D, 0.5D));
    target.set(hollow);

    assertThat(work.seek(golem)).isEmpty();
    assertThat(work.act(golem, pickup(hollow))).isEqualTo(GolemActResult.FAILED);

    assertThat(carriedStacks).isEmpty();
    assertThat(removed).doesNotContain(hollow);
  }

  @Test
  void pickingUpASuperStackTakesTheItemsAndRemovesTheEmptyBundle() {
    Item bundle = item(ReactBundles.superStack(wheat(4), carrot(3)), at(2.5D, 64D, 0.5D));
    target.set(bundle);

    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.DONE);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx3");
    assertThat(carriedStacks).noneMatch(stack -> stack.getType() == Material.BUNDLE);
    assertThat(carried.get()).isEqualTo(7);
    assertThat(removed).contains(bundle);
    assertThat(dropped).isEmpty();
    assertThat(probedRemaining).containsExactly(0);
    assertThat(probedItems).containsExactly(bundle);
    assertThat(workXp).containsExactly(0.5D);
  }

  @Test
  void aSuperStackBiggerThanTheRoomKeepsTheRestAsASuperStackInOrder() {
    capacity.set(10);
    Item bundle = item(ReactBundles.superStack(wheat(4), carrot(4), GolemTestItems.stack(Material.DIAMOND, 6)), at(2.5D, 64D, 0.5D));
    target.set(bundle);

    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.DONE);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx4", "DIAMONDx2");
    assertThat(carried.get()).isEqualTo(10);
    assertThat(removed).doesNotContain(bundle);
    assertThat(ReactSuperStacks.isSuperStack(bundle.getItemStack())).isTrue();
    assertThat(ReactBundles.lore(bundle.getItemStack())).containsExactly(ReactBundles.MARKER);
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle.getItemStack()))).containsExactly("DIAMONDx4");
    assertThat(dropped).isEmpty();
  }

  @Test
  void aSuperStackTheGolemCannotTakeAnythingFromIsLeftWhole() {
    carried.set(64);
    Item bundle = item(ReactBundles.superStack(wheat(4)), at(2.5D, 64D, 0.5D));
    target.set(bundle);
    ItemStack before = bundle.getItemStack();

    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.FAILED);

    assertThat(carriedStacks).isEmpty();
    assertThat(bundle.getItemStack()).isSameAs(before);
    assertThat(removed).doesNotContain(bundle);
    assertThat(probedRemaining).isEmpty();
  }

  @Test
  void leftoversAReplacementBundleCannotHoldAreDroppedAsPlainItems() {
    capacity.set(5);
    Item bundle = item(ReactBundles.rejecting(wheat(4), carrot(4), GolemTestItems.stack(Material.DIAMOND, 2)), at(2.5D, 64D, 0.5D));
    target.set(bundle);

    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.DONE);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx1");
    assertThat(removed).contains(bundle);
    assertThat(ReactBundles.summary(dropped)).containsExactly("CARROTx3", "DIAMONDx2");
    assertThat(dropped).noneMatch(stack -> stack.getType() == Material.BUNDLE);
  }

  @Test
  void aBundleIsOnlyUnpackedWhileReactIsEnabledAndTheMarkerIsExact() {
    Item lookalike = item(ReactBundles.bundle(List.of("REACT SUPER STACK", "mine"), 1, wheat(4)), at(1.5D, 64D, 0.5D));
    target.set(lookalike);

    assertThat(work.act(golem, pickup(lookalike))).isEqualTo(GolemActResult.DONE);
    assertThat(carriedStacks).extracting(ItemStack::getType).containsExactly(Material.BUNDLE);
    assertThat(removed).contains(lookalike);

    carriedStacks.clear();
    reactEnabled.set(false);
    Item bundle = item(ReactBundles.superStack(wheat(4)), at(2.5D, 64D, 0.5D));
    target.set(bundle);
    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.DONE);
    assertThat(carriedStacks).extracting(ItemStack::getType).containsExactly(Material.BUNDLE);
    assertThat(removed).contains(bundle);
  }

  @Test
  void theProtectionProbeAndTheClaimCheckStillGuardASuperStack() {
    Item bundle = item(ReactBundles.superStack(wheat(4)), at(2.5D, 64D, 0.5D));
    target.set(bundle);
    probe.set(false);

    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.BLOCKED);
    assertThat(probedRemaining).containsExactly(0);

    probe.set(true);
    interact.set(location -> false);
    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.BLOCKED);
    interact.set(location -> true);
    breakable.set(location -> false);
    assertThat(work.act(golem, pickup(bundle))).isEqualTo(GolemActResult.BLOCKED);

    assertThat(probedRemaining).containsExactly(0);
    assertThat(carriedStacks).isEmpty();
    assertThat(removed).doesNotContain(bundle);
    assertThat(ReactBundles.summary(ReactBundles.contents(bundle.getItemStack()))).containsExactly("WHEATx4");
  }

  @Test
  void aSuperStackFollowsTheEligibilityAndDeathSiteRules() {
    Item thrown = item(ReactBundles.superStack(wheat(4)), at(2.5D, 64D, 0.5D));
    when(thrown.getThrower()).thenReturn(STRANGER);
    Item waiting = item(ReactBundles.superStack(wheat(4)), at(1.5D, 64D, 0.5D));
    waiting.setPickupDelay(10);
    Item grave = item(ReactBundles.superStack(wheat(4)), at(0.5D, 64D, 3.5D));
    deathSites.record(WORLD, 0.5D, 64D, 3.5D, clock.get(), FIVE_MINUTES);

    assertThat(work.seek(golem)).isEmpty();
    target.set(thrown);
    assertThat(work.act(golem, pickup(thrown))).isEqualTo(GolemActResult.FAILED);
    target.set(waiting);
    assertThat(work.act(golem, pickup(waiting))).isEqualTo(GolemActResult.FAILED);
    target.set(grave);
    assertThat(work.act(golem, pickup(grave))).isEqualTo(GolemActResult.FAILED);

    assertThat(carriedStacks).isEmpty();
  }

  @Test
  void instantPickupUnpacksASuperStackWithinReach() {
    level.set(5);
    Item bundle = item(ReactBundles.superStack(wheat(4), carrot(3)), at(1.0D, 64D, 0.5D));

    work.tick(golem);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("WHEATx4", "CARROTx3");
    assertThat(removed).contains(bundle);
    assertThat(velocities).isEmpty();
  }

  @Test
  void theMagnetPullsASuperStackTowardTheGolemWithoutUnpackingIt() {
    level.set(4);
    Item bundle = item(ReactBundles.superStack(wheat(4)), at(2.5D, 64D, 0.5D));

    work.tick(golem);

    assertThat(velocities).containsOnlyKeys(bundle);
    assertThat(carriedStacks).isEmpty();
    assertThat(removed).doesNotContain(bundle);
  }

  @Test
  void aCollectionNodeFilterAppliesToWhatIsInsideASuperStack() {
    level.set(5);
    collectionNode(new NodeKey(WORLD, 0, 63, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(3, 1, 3))
        .filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build());
    Item mixed = item(ReactBundles.superStack(wheat(2), carrot(3), GolemTestItems.stack(Material.DIAMOND, 1)), at(1.0D, 64D, 0.5D));
    Item wheatOnly = item(ReactBundles.superStack(wheat(5)), at(0.5D, 64D, 1.5D));

    work.tick(golem);

    assertThat(ReactBundles.summary(carriedStacks)).containsExactly("CARROTx3");
    assertThat(removed).doesNotContain(mixed, wheatOnly);
    assertThat(ReactBundles.summary(ReactBundles.contents(mixed.getItemStack()))).containsExactly("WHEATx2", "DIAMONDx1");
    assertThat(ReactBundles.summary(ReactBundles.contents(wheatOnly.getItemStack()))).containsExactly("WHEATx5");
  }

  @Test
  void aSuperStackNeverReachesTheCarryWhateverTheRoom() {
    for (int room = 0; room <= 12; room++) {
      carried.set(64 - room);
      carriedStacks.clear();
      Item bundle = item(ReactBundles.superStack(wheat(4), carrot(4), GolemTestItems.stack(Material.DIAMOND, 4)), at(2.5D, 64D, 0.5D));
      target.set(bundle);

      work.act(golem, pickup(bundle));

      assertThat(carriedStacks).as("room %d", room).noneMatch(stack -> stack.getType() == Material.BUNDLE);
      int left = removed.contains(bundle) ? 0 : ReactBundles.contents(bundle.getItemStack()).stream().mapToInt(ItemStack::getAmount).sum();
      assertThat(carriedStacks.stream().mapToInt(ItemStack::getAmount).sum() + left).as("room %d", room).isEqualTo(12);
    }
  }

  private static ItemStack wheat(int amount) {
    return GolemTestItems.stack(Material.WHEAT, amount);
  }

  private static ItemStack carrot(int amount) {
    return GolemTestItems.stack(Material.CARROT, amount);
  }

  private GolemTask pickup(Entity entity) {
    return GolemTask.builder(GathererWork.PICKUP, GolemTarget.entity(entity)).build();
  }

  private GolemTask absorbTask(Entity entity) {
    return GolemTask.builder(GathererWork.ABSORB, GolemTarget.entity(entity)).build();
  }

  private void collectionNode(NodeKey key, NodeRecord record) {
    nodes.add(new NodeIndex.Entry(key, record));
  }

  private void tide(double bonus) {
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
      public GolemRuneEffects effects(GolemHandle ignored) {
        return new GolemRuneEffects(bonus, 0, 1D, 0D);
      }
    };
    GolemRunes.register(fixed);
    augments.add(fixed);
  }

  private Item item(Material type, int amount, Location location) {
    return item(GolemTestItems.stack(type, amount), location);
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
    entityLifecycle(item);
    return item;
  }

  private ExperienceOrb orb(int experience, int count, Location location) {
    ExperienceOrb orb = mock(ExperienceOrb.class, withSettings().strictness(Strictness.LENIENT));
    AtomicReference<Location> where = new AtomicReference<>(location);
    UUID id = UUID.randomUUID();
    positions.put(orb, where);
    when(orb.getUniqueId()).thenReturn(id);
    when(orb.getWorld()).thenReturn(world);
    when(orb.getLocation()).thenAnswer(invocation -> where.get().clone());
    when(orb.getExperience()).thenReturn(experience);
    when(orb.getCount()).thenReturn(count);
    when(orb.isValid()).thenAnswer(invocation -> !removed.contains(orb));
    entityLifecycle(orb);
    return orb;
  }

  private void entityLifecycle(Entity entity) {
    doAnswer(invocation -> {
      removed.add(entity);
      nearby.remove(entity);
      return null;
    }).when(entity).remove();
    doAnswer(invocation -> {
      velocities.put(entity, invocation.getArgument(0));
      return null;
    }).when(entity).setVelocity(any(Vector.class));
    nearby.add(entity);
  }

  private GolemHandle handle() {
    GolemState state = mock(GolemState.class, withSettings().strictness(Strictness.LENIENT));
    when(state.home()).thenAnswer(invocation -> home.get());
    when(state.channels()).thenReturn(ChannelMask.NONE);
    when(state.cores()).thenReturn(List.of(GolemCoreType.GATHERER));
    when(state.essence()).thenAnswer(invocation -> essence.get());
    when(state.essencePoints()).thenAnswer(invocation -> (int) Math.floor(essence.get()));
    GolemHandle bound = mock(GolemHandle.class, withSettings().strictness(Strictness.LENIENT));
    UUID id = UUID.randomUUID();
    when(bound.id()).thenReturn(id);
    when(bound.owner()).thenReturn(OWNER);
    when(bound.body()).thenReturn(GolemBody.IRON);
    when(bound.core()).thenReturn(GolemCoreType.GATHERER);
    when(bound.entity()).thenReturn(body);
    when(bound.state()).thenReturn(state);
    doAnswer(invocation -> {
      essence.set(invocation.getArgument(0));
      return null;
    }).when(bound).setEssence(anyDouble());
    return bound;
  }

  private GolemContext context() {
    GolemContext context = mock(GolemContext.class, withSettings().strictness(Strictness.LENIENT));
    when(context.handle()).thenReturn(handle);
    when(context.golem()).thenReturn(body);
    when(context.owner()).thenReturn(owner);
    when(context.world()).thenReturn(world);
    when(context.core()).thenReturn(GolemCoreType.GATHERER);
    when(context.nowMillis()).thenAnswer(invocation -> clock.get());
    when(context.home()).thenAnswer(invocation -> homeLocation());
    when(context.workRadius()).thenAnswer(invocation -> workRadius.get());
    when(context.inWorkRadius(any(Location.class))).thenAnswer(invocation -> {
      Location location = invocation.getArgument(0);
      return location.getWorld() == world && location.distance(homeLocation()) <= workRadius.get();
    });
    when(context.skips(any(GolemTask.class))).thenAnswer(invocation -> skipped.contains(invocation.<GolemTask>getArgument(0).target().entity()));
    when(context.targetEntity()).thenAnswer(invocation -> Optional.ofNullable(target.get()).filter(entity -> !removed.contains(entity)));
    when(context.capacity()).thenAnswer(invocation -> capacity.get());
    when(context.carried()).thenAnswer(invocation -> carried.get());
    when(context.room()).thenAnswer(invocation -> Math.max(0, capacity.get() - carried.get()));
    when(context.carry(any(ItemStack.class))).thenAnswer(invocation -> {
      ItemStack stack = invocation.getArgument(0);
      int taking = Math.min(stack.getAmount(), Math.max(0, capacity.get() - carried.get()));
      if (taking > 0) {
        ItemStack taken = stack.clone();
        taken.setAmount(taking);
        carriedStacks.add(taken);
      }
      carried.addAndGet(taking);
      if (taking == stack.getAmount()) {
        return Optional.empty();
      }
      ItemStack rest = stack.clone();
      rest.setAmount(stack.getAmount() - taking);
      return Optional.of(rest);
    });
    when(context.canInteract(any(Location.class))).thenAnswer(invocation -> interact.get().test(invocation.getArgument(0)));
    when(context.canBreak(any(Location.class))).thenAnswer(invocation -> breakable.get().test(invocation.getArgument(0)));
    doAnswer(invocation -> {
      workXp.add(invocation.getArgument(0));
      return null;
    }).when(context).workXp(anyDouble());
    return context;
  }

  private Location homeLocation() {
    GolemState.Anchor anchor = home.get();
    return new Location(world, anchor.x() + 0.5D, anchor.y(), anchor.z() + 0.5D);
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x, y, z);
  }

  private World world(UUID id) {
    World created = mock(World.class, withSettings().strictness(Strictness.LENIENT));
    when(created.getUID()).thenReturn(id);
    when(created.dropItemNaturally(any(Location.class), any(ItemStack.class))).thenAnswer(invocation -> {
      dropped.add(invocation.getArgument(1));
      return mock(Item.class);
    });
    when(created.getNearbyEntities(any(BoundingBox.class), any())).thenAnswer(invocation -> {
      BoundingBox box = invocation.getArgument(0);
      Predicate<Entity> filter = invocation.getArgument(1);
      List<Entity> found = new ArrayList<>();
      for (Entity entity : nearby) {
        Location location = positions.get(entity).get();
        if (location.getWorld() == created && box.contains(location.getX(), location.getY(), location.getZ()) && filter.test(entity)) {
          found.add(entity);
        }
      }
      return found;
    });
    return created;
  }
}
