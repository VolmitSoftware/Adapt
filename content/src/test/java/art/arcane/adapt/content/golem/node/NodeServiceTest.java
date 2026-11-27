package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.HiddenDisplays;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.reveal.RevealService;
import art.arcane.adapt.content.golem.reveal.RevealSettings;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.ItemSpawnEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.event.world.WorldLifecycleEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeServiceTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  private final FakeNodePlatform platform = new FakeNodePlatform();
  private final FakePolicy policy = new FakePolicy();
  private final Map<String, Id> blocks = new HashMap<>();
  private final Map<NodeType, ItemRef> nodeItems = new EnumMap<>(NodeType.class);
  private final List<EntityRef> spawned = new ArrayList<>();
  private final List<Throwable> logged = new CopyOnWriteArrayList<>();
  private final PlatformLog capture = new PlatformLog() {
    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void error(String message, Throwable error) {
      if (error != null) {
        logged.add(error);
      }
    }

    @Override
    public void verbose(String message) {
    }
  };
  private MockedStatic<HiddenDisplays> displays;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private RevealService reveal;
  private WorldRef world;
  private NodeService service;

  @BeforeEach
  void setUp() {
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.name()).thenReturn("world");
    lenient().when(world.isLoaded()).thenReturn(true);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    lenient().when(world.blockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    platform.worlds.put(WORLD, world);
    displays = mockStatic(HiddenDisplays.class);
    displays.when(() -> HiddenDisplays.spawnFaceItem(any(BlockRef.class), any(BlockFace.class), any(ItemRef.class), anyFloat(), any(Vec3.class)))
        .thenAnswer(invocation -> entity(ItemDisplayRef.class));
    displays.when(() -> HiddenDisplays.spawnFaceHitbox(any(BlockRef.class), any(BlockFace.class), anyFloat(), anyFloat()))
        .thenAnswer(invocation -> entity(EntityRef.class));
    displays.when(() -> HiddenDisplays.spawnLabel(any(Loc.class), anyString(), anyInt(), anyFloat()))
        .thenAnswer(invocation -> entity(TextDisplayRef.class));
    items = mockStatic(GolemItems.class);
    for (NodeType type : NodeType.values()) {
      ItemRef item = mock(ItemRef.class);
      nodeItems.put(type, item);
      items.when(() -> GolemItems.node(type)).thenReturn(item);
    }
    reveal = mock(RevealService.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.of(reveal));
    lenient().when(core.getPlatform().scheduler().owns(any(EntityRef.class))).thenReturn(true);
    AdaptEnvironment.install(capture, dataFolder::toPath);
    service = new NodeService(platform, NodeSettings::defaults, RevealSettings::defaults);
  }

  @AfterEach
  void tearDown() {
    runtime.close();
    items.close();
    displays.close();
  }

  @Test
  void loadingAChunkKeepsEveryNodeWhoseAnchorIsGone() {
    blocks.put("2,64,1", Id.minecraft("stone"));
    NodeRecord guard = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    NodeRecord deposit = NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.NORTH).build();
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(guard));
    platform.put(WORLD, 2, 64, 1, NodeFaces.EMPTY.with(deposit));
    GolemRoster roster = onlineRoster(OWNER);
    roster.addNode(GolemRoster.NodeEntry.at(GolemAnchor.of(WORLD, 1, 64, 1), art.arcane.adapt.platform.value.BlockFace.UP, NodeType.GUARD));
    roster.addNode(GolemRoster.NodeEntry.at(GolemAnchor.of(WORLD, 2, 64, 1), art.arcane.adapt.platform.value.BlockFace.NORTH, NodeType.DEPOSIT));

    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));

    NodeKey guardKey = new NodeKey(WORLD, 1, 64, 1, BlockFace.UP);
    NodeKey depositKey = new NodeKey(WORLD, 2, 64, 1, BlockFace.NORTH);
    assertThat(service.node(guardKey)).contains(guard);
    assertThat(service.node(depositKey)).contains(deposit);
    assertThat(platform.stored(WORLD, 1, 64, 1).get(BlockFace.UP)).isEqualTo(guard);
    assertThat(platform.stored(WORLD, 2, 64, 1).get(BlockFace.NORTH)).isEqualTo(deposit);
    assertThat(roster.nodeCount()).isEqualTo(2);
    assertThat(platform.events).isEmpty();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    assertThat(service.renderer().bundle(guardKey)).isPresent();
    assertThat(service.renderer().bundle(depositKey)).isPresent();
    verify(reveal).nodeSpawned(guardKey);
    verify(reveal).nodeSpawned(depositKey);
  }

  @Test
  void placementWritesTheRecordAndRosterEntryThenRendersAndSchedulesAScan() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    GolemRoster roster = onlineRoster(OWNER);

    NodePlacementVerdict verdict = service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    assertThat(verdict).isEqualTo(NodePlacementVerdict.READY);
    NodeRecord stored = platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP);
    assertThat(stored.owner()).isEqualTo(OWNER);
    assertThat(stored.type()).isEqualTo(NodeType.GUARD);
    assertThat(stored.area()).isEqualTo(NodeRecord.DEFAULT_AREA).isEqualTo(new NodeCuboid(-2, -2, -2, 2, 2, 2));
    assertThat(service.node(key)).contains(stored);
    assertThat(roster.nodes()).extracting(GolemRoster.NodeEntry::type).containsExactly(NodeType.GUARD);
    assertThat(platform.rosterSaves).containsEntry(OWNER, 1);
    assertThat(service.renderer().bundle(key)).isPresent();
    assertThat(platform.scheduled).hasSize(1);
  }

  @Test
  void theDefaultAreaNeverExceedsTheOwnersRadiusCap() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    policy.maxAreaRadius = 1;

    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);

    NodeRecord stored = platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP);
    assertThat(stored.area()).isEqualTo(NodeCuboid.symmetric(1, 1, 1));
  }

  @Test
  void placementIsRefusedOnAnOccupiedFaceAtTheCapAndOnNonContainers() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    GolemRoster roster = onlineRoster(OWNER);
    PlayerRef owner = player(OWNER);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    assertThat(service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy)).isEqualTo(NodePlacementVerdict.OCCUPIED);
    assertThat(service.place(owner, block(3, 64, 3), BlockFace.NORTH, NodeType.DEPOSIT, policy)).isEqualTo(NodePlacementVerdict.NEEDS_CONTAINER);
    policy.nodeCap = 1;
    assertThat(service.place(owner, block(3, 64, 3), BlockFace.EAST, NodeType.HARVEST, policy)).isEqualTo(NodePlacementVerdict.NODE_CAP);
    policy.nodeCap = 4;
    policy.canPlace = false;
    assertThat(service.place(owner, block(3, 64, 3), BlockFace.EAST, NodeType.HARVEST, policy)).isEqualTo(NodePlacementVerdict.PROTECTED);
    assertThat(roster.nodeCount()).isEqualTo(1);
  }

  @Test
  void fishingNodesAreRefusedOnTopAndBottomFacesAndPlaceOnSides() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    GolemRoster roster = onlineRoster(OWNER);
    PlayerRef owner = player(OWNER);

    assertThat(service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.FISHING, policy)).isEqualTo(NodePlacementVerdict.NEEDS_SIDE_FACE);
    assertThat(service.place(owner, block(3, 64, 3), BlockFace.DOWN, NodeType.FISHING, policy)).isEqualTo(NodePlacementVerdict.NEEDS_SIDE_FACE);
    assertThat(roster.nodeCount()).isZero();
    assertThat(service.place(owner, block(3, 64, 3), BlockFace.NORTH, NodeType.FISHING, policy)).isEqualTo(NodePlacementVerdict.READY);
    assertThat(roster.nodeCount()).isEqualTo(1);
  }

  @Test
  void containerNodesNeedChestAccess() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("chest"));
    onlineRoster(OWNER);
    policy.canAccessContainer = false;

    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.NORTH, NodeType.DEPOSIT, policy))
        .isEqualTo(NodePlacementVerdict.CONTAINER_PROTECTED);
    policy.canAccessContainer = true;
    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.NORTH, NodeType.DEPOSIT, policy))
        .isEqualTo(NodePlacementVerdict.READY);
  }

  @Test
  void placementWaitsForTheChunkIndexAndTheRoster() {
    blocks.put("3,64,3", Id.minecraft("stone"));

    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy)).isEqualTo(NodePlacementVerdict.UNAVAILABLE);
    onlineRoster(OWNER);
    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy)).isEqualTo(NodePlacementVerdict.NOT_LOADED);
  }

  @Test
  void aNodeOutsideTheCurrentRegionIsNeitherPlacedNorRemoved() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    GolemRoster roster = onlineRoster(OWNER);
    PlayerRef owner = player(OWNER);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);
    platform.ownsEverything = false;

    assertThat(service.place(owner, block(3, 64, 3), BlockFace.EAST, NodeType.GUARD, policy)).isEqualTo(NodePlacementVerdict.NOT_LOADED);
    assertThat(service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP))).isFalse();
    assertThat(roster.nodeCount()).isEqualTo(1);
  }

  @Test
  void theChunkCapCountsEveryOwnersNodes() {
    service = new NodeService(platform, () -> new NodeSettings(1, 40, 64, 16, NodeVisibility.OWNER), RevealSettings::defaults);
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    onlineRoster(STRANGER);
    service.place(player(STRANGER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.EAST, NodeType.GUARD, policy)).isEqualTo(NodePlacementVerdict.CHUNK_FULL);
  }

  @Test
  void theOwnerGetsTheItemBackWhenRemovingANodeAndStrangersCannotRemoveIt() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    GolemRoster roster = onlineRoster(OWNER);
    PlayerRef owner = player(OWNER);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    assertThat(service.remove(player(STRANGER), key)).isFalse();
    assertThat(service.remove(owner, key)).isTrue();

    assertThat(service.node(key)).isEmpty();
    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
    assertThat(roster.nodeCount()).isZero();
    verify(owner.inventory()).addItem(nodeItems.get(NodeType.GUARD));
    verify(bundle.icon()).remove();
    verify(bundle.hitbox()).remove();
    verify(bundle.label()).remove();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    verify(reveal).nodeGone(key);
  }

  @ParameterizedTest
  @EnumSource(NodeType.class)
  void aScanKeepsEveryNodeTypeWhoseAnchorBrokeSinceTheLastPass(NodeType type) {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("chest"));
    GolemRoster roster = onlineRoster(OWNER);
    service.place(player(OWNER), block(3, 64, 3), BlockFace.NORTH, type, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.NORTH);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    platform.runScheduled();
    assertThat(platform.scheduled).hasSize(1);
    assertThat(platform.scheduled.getFirst().delayTicks()).isEqualTo(NodeSettings.defaults().scanIntervalTicks());

    blocks.put("3,64,3", Id.minecraft("air"));
    platform.runScheduled();
    platform.runAsync();

    assertThat(service.node(key)).isPresent();
    assertThat(platform.scheduled).hasSize(1);
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.NORTH)).isNotNull();
    assertThat(roster.nodeCount()).isEqualTo(1);
    assertThat(service.renderer().bundle(key)).containsSame(bundle);
    assertThat(platform.events).isEmpty();
    verify(bundle.icon(), never()).remove();
    verify(bundle.hitbox(), never()).remove();
    verify(bundle.label(), never()).remove();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    verify(reveal, never()).nodeGone(key);
  }

  @Test
  void aNodeKeepsScanningThroughAnAnchorThatIsLostAndReplaced() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);

    for (Id material : List.of(Id.minecraft("air"), Id.minecraft("water"), Id.minecraft("lava"), Id.minecraft("fire"), Id.minecraft("oak_leaves"), Id.minecraft("stone"))) {
      blocks.put("3,64,3", material);
      platform.runScheduled();
      assertThat(service.node(key)).as(material.toString()).isPresent();
      assertThat(platform.scheduled).as(material.toString()).hasSize(1);
    }
    assertThat(platform.events).isEmpty();
  }

  @Test
  void aFaceStaysOccupiedAfterItsAnchorBreaks() {
    PlayerRef owner = placedGuard();
    blocks.put("3,64,3", Id.minecraft("air"));
    platform.runScheduled();

    NodePlacementVerdict verdict = service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);

    assertThat(verdict).isEqualTo(NodePlacementVerdict.OCCUPIED);
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP).type()).isEqualTo(NodeType.GUARD);
  }

  @ParameterizedTest
  @EnumSource(NodeType.class)
  void theOwnerRemovesAnyNodeWhoseAnchorIsGoneAndGetsTheItemBack(NodeType type) {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("chest"));
    GolemRoster roster = onlineRoster(OWNER);
    PlayerRef owner = player(OWNER);
    service.place(owner, block(3, 64, 3), BlockFace.NORTH, type, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.NORTH);
    blocks.put("3,64,3", Id.minecraft("air"));
    platform.runScheduled();
    assertThat(service.node(key)).isPresent();

    assertThat(service.remove(player(STRANGER), key)).isFalse();
    assertThat(service.node(key)).isPresent();
    assertThat(service.remove(owner, key)).isTrue();

    assertThat(service.node(key)).isEmpty();
    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
    assertThat(roster.nodeCount()).isZero();
    assertThat(service.renderer().bundle(key)).isEmpty();
    verify(owner.inventory()).addItem(nodeItems.get(type));
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    verify(reveal).nodeGone(key);
  }

  @Test
  void anOldScanChainStopsWhenTheNodeIsReplacedAtTheSameFace() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    PlayerRef owner = player(OWNER);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);
    service.remove(owner, key);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);
    assertThat(platform.scheduled).hasSize(2);

    platform.runScheduled();

    assertThat(platform.scheduled).hasSize(1);
  }

  @Test
  void theScanHookReceivesAPersistentCursorWindowOverTheWorkArea() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    List<NodeScanPass> passes = new ArrayList<>();
    service.setScanHook(passes::add);
    policy.maxAreaRadius = 2;
    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);

    platform.runScheduled();
    platform.runScheduled();

    assertThat(passes).hasSize(2);
    assertThat(passes.get(0).window().indices()).containsExactly(IntStream.range(0, 64).toArray());
    assertThat(passes.get(1).window().indices()).containsExactly(IntStream.range(64, 128).map(index -> index % 125).toArray());
    assertThat(passes.get(0).maxOpenTasks()).isEqualTo(16);
  }

  @Test
  void unloadingTheChunkDespawnsBundlesAndForgetsTheNodes() {
    blocks.put("1,64,1", Id.minecraft("stone"));
    NodeRecord guard = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(guard));
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    NodeKey key = new NodeKey(WORLD, 1, 64, 1, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.UNLOAD, false));

    assertThat(service.node(key)).isEmpty();
    assertThat(service.renderer().bundle(key)).isEmpty();
    assertThat(service.renderer().nodeOf(bundle.hitbox())).isEmpty();
    verify(bundle.icon()).remove();
    verify(reveal).nodeGone(key);
    assertThat(platform.stored(WORLD, 1, 64, 1).get(BlockFace.UP)).isEqualTo(guard);
  }

  @Test
  void bundleEntitiesMapBackToTheirNode() {
    blocks.put("1,64,1", Id.minecraft("stone"));
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build()));
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    NodeKey key = new NodeKey(WORLD, 1, 64, 1, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    assertThat(service.renderer().nodeOf(bundle.hitbox())).contains(key);
    assertThat(service.renderer().nodeOf(bundle.icon())).contains(key);
    assertThat(service.renderer().nodeOf(bundle.label())).contains(key);
  }

  @Test
  void joiningDropsRosterEntriesThatNoLongerHaveAMantleRecord() {
    GolemRoster roster = onlineRoster(OWNER);
    platform.put(WORLD, 5, 64, 5, NodeFaces.EMPTY.with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build()));
    roster.addNode(GolemRoster.NodeEntry.at(GolemAnchor.of(WORLD, 5, 64, 5), art.arcane.adapt.platform.value.BlockFace.UP, NodeType.GUARD));
    roster.addNode(GolemRoster.NodeEntry.at(GolemAnchor.of(WORLD, 6, 64, 6), art.arcane.adapt.platform.value.BlockFace.UP, NodeType.GUARD));
    roster.addNode(GolemRoster.NodeEntry.at(GolemAnchor.of(WORLD, 5, 64, 5), art.arcane.adapt.platform.value.BlockFace.NORTH, NodeType.DEPOSIT));
    PlayerRef joined = player(OWNER);

    service.on(new PlayerJoinEvent(joined));
    platform.runAsync();

    assertThat(roster.nodes()).extracting(GolemRoster.NodeEntry::face).containsExactly(art.arcane.adapt.platform.value.BlockFace.UP);
    assertThat(roster.nodes().getFirst().block().x()).isEqualTo(5);
    assertThat(platform.rosterSaves).containsEntry(OWNER, 1);
  }

  @Test
  void aRemovedNodesItemIsHandedOutOnlyAfterItsPlateIsSaved() {
    PlayerRef owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    platform.deferAsync = true;

    assertThat(service.remove(owner, key)).isTrue();

    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP).data()).containsEntry("adapt:return", "pending");
    verify(owner.inventory(), never()).addItem(any(ItemRef.class));
    platform.runAsync();
    assertThat(platform.events).containsExactly("save 3,3");
    platform.drainAsync();
    verify(owner.inventory()).addItem(nodeItems.get(NodeType.GUARD));
  }

  @Test
  void aFailedSaveLogsItsStackTraceAndWithholdsTheItem() {
    PlayerRef owner = placedGuard();
    platform.saveFailure = new IllegalStateException("disk full");

    assertThat(service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP))).isTrue();

    verify(owner.inventory(), never()).addItem(any(ItemRef.class));
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    assertThat(logged).anySatisfy(thrown -> assertThat(thrown).hasMessage("disk full"));
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP).data()).containsEntry("adapt:return", "pending");
  }

  @Test
  void aPlateThatStaysBusyKeepsItsPendingReturnForALaterRetry() {
    PlayerRef owner = placedGuard();
    platform.busySaves = 1000;

    service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    platform.drainAsync();

    assertThat(platform.events).hasSize(20);
    verify(owner.inventory(), never()).addItem(any(ItemRef.class));
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP).data()).containsEntry("adapt:return", "pending");
  }

  @Test
  void aPlateThatFreesUpIsSavedAndTheItemHandedOutOnce() {
    PlayerRef owner = placedGuard();
    platform.busySaves = 2;

    service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    platform.drainAsync();

    assertThat(platform.events).hasSize(5);
    verify(owner.inventory()).addItem(nodeItems.get(NodeType.GUARD));
  }

  @Test
  void anOwnerWhoLeftTheRegionGetsTheItemDroppedAtTheNode() {
    PlayerRef owner = placedGuard();
    platform.deferAsync = true;
    service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    when(owner.isOnline()).thenReturn(false);

    platform.drainAsync();

    verify(owner.inventory(), never()).addItem(any(ItemRef.class));
    verify(world).dropItemNaturally(any(Vec3.class), eq(nodeItems.get(NodeType.GUARD)));
  }

  @Test
  void aNodeWhoseStoredRecordIsAlreadyGoneYieldsNoItem() {
    PlayerRef owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    platform.store.clear();

    assertThat(service.remove(owner, key)).isFalse();

    assertThat(service.node(key)).isEmpty();
    assertThat(platform.events).isEmpty();
    verify(owner.inventory(), never()).addItem(any(ItemRef.class));
  }

  @Test
  void aFailureAfterTheNodeIsStoredStillReportsReadySoTheItemIsConsumed() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    displays.when(() -> HiddenDisplays.spawnFaceItem(any(BlockRef.class), any(BlockFace.class), any(ItemRef.class), anyFloat(), any(Vec3.class)))
        .thenThrow(new IllegalStateException("display failed"));

    NodePlacementVerdict verdict = service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    assertThat(verdict).isEqualTo(NodePlacementVerdict.READY);
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP)).isNotNull();
    assertThat(logged).anySatisfy(thrown -> assertThat(thrown).hasMessage("display failed"));
  }

  @Test
  void placementReplacesAStaleRosterEntryOnTheSameFace() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    GolemRoster roster = onlineRoster(OWNER);
    roster.addNode(GolemRoster.NodeEntry.at(GolemAnchor.of(WORLD, 3, 64, 3), art.arcane.adapt.platform.value.BlockFace.UP, NodeType.HARVEST));

    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    assertThat(roster.nodes()).extracting(GolemRoster.NodeEntry::type).containsExactly(NodeType.GUARD);
  }

  @Test
  void newChunksAreReadyWithoutReadingTheMantle() {
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, true));

    assertThat(platform.chunkReads).isZero();
    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isTrue();
  }

  @Test
  void anEmptyChunkReadCompletesWithoutHoppingToTheRegion() {
    platform.refuseRunAt = true;

    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));

    assertThat(platform.chunkReads).isEqualTo(1);
    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isTrue();
  }

  @Test
  void aRefusedRegionHopOnlyForgetsItsOwnLoad() {
    blocks.put("1,64,1", Id.minecraft("stone"));
    NodeRecord guard = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(guard));
    platform.deferAsync = true;
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    Runnable stale = platform.asyncTasks.get(0);
    Runnable current = platform.asyncTasks.get(1);

    current.run();
    platform.refuseRunAt = true;
    stale.run();

    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isTrue();
    assertThat(service.node(new NodeKey(WORLD, 1, 64, 1, BlockFace.UP))).contains(guard);
  }

  @Test
  void theOwnersEditIsStoredIndexedAndRefreshesTheLabelAndGlow() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    Optional<NodeRecord> updated = service.update(OWNER, key, record -> record.toBuilder().channel(DyeColor.RED).priority(5).build());

    assertThat(updated).map(NodeRecord::channel).contains(DyeColor.RED);
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP)).isEqualTo(updated.get());
    assertThat(service.node(key)).isEqualTo(updated);
    assertThat(service.renderer().bundle(key)).containsSame(bundle);
    verify(bundle.label()).setText(anyString());
    verify(bundle.icon()).setGlowColorOverride(DyeColor.RED.color());
    verify(bundle.icon(), atLeastOnce()).setGlowing(true);
  }

  @Test
  void statusChangesRedrawTheLabelOnceAndSurviveAnEdit() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    service.status(key, NodeStatus.WORKING);
    service.status(key, NodeStatus.WORKING);

    verify(bundle.label(), times(1)).setText(anyString());

    service.update(OWNER, key, record -> record.toBuilder().priority(5).build());
    service.status(key, NodeStatus.WORKING);

    ArgumentCaptor<String> labels = ArgumentCaptor.forClass(String.class);
    verify(bundle.label(), times(2)).setText(labels.capture());
    assertThat(labels.getValue()).endsWith("</" + NodeStatus.WORKING.color() + ">");
    service.status(new NodeKey(WORLD, 3, 64, 3, BlockFace.EAST), NodeStatus.FULL);
    assertThat(service.renderer().bundle(new NodeKey(WORLD, 3, 64, 3, BlockFace.EAST))).isEmpty();
    verify(bundle.label(), times(2)).setText(anyString());
  }

  @Test
  void aRemoteScanStatusReturnsToTheAnchorRegionBeforeChangingTheLabel() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();
    platform.scheduled.clear();
    platform.ownsEverything = false;

    service.status(key, NodeStatus.WORKING);

    verify(bundle.label(), never()).setText(anyString());
    assertThat(platform.scheduled).singleElement().satisfies(task -> assertThat(task.location()).isEqualTo(key.center(world)));
    platform.ownsEverything = true;
    platform.runScheduled();
    verify(bundle.label()).setText(anyString());
  }

  @Test
  void aQueuedStatusCannotChangeAReplacementScanGeneration() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeRecord original = service.node(key).orElseThrow();
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();
    platform.scheduled.clear();
    platform.ownsEverything = false;
    service.status(key, NodeStatus.BLOCKED);
    platform.ownsEverything = true;
    service.update(OWNER, key, record -> record.toBuilder().priority(5).build());
    service.index().put(key, original);
    clearInvocations(bundle.label());

    platform.runScheduled();

    verify(bundle.label(), never()).setText(anyString());
  }

  @Test
  void renderingFailuresKeepTheFullStackOnceAndDoNotEscape() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();
    IllegalStateException failure = new IllegalStateException("label update failed");
    doThrow(failure).when(bundle.label()).setText(anyString());

    service.status(key, NodeStatus.WORKING);
    service.status(key, NodeStatus.BLOCKED);

    assertThat(logged).filteredOn(thrown -> thrown == failure).hasSize(1);
    assertThat(platform.scheduled).hasSize(1);
  }

  @Test
  void aFailingRemoteBatchStillSchedulesTheNextPassAndLogsItsFullStackOnce() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    IllegalStateException failure = new IllegalStateException("scan callback failed");
    service.setScanHook(pass -> {
      throw failure;
    });
    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);

    platform.runScheduled();
    assertThat(platform.scheduled).hasSize(1);
    platform.runScheduled();

    assertThat(platform.scheduled).hasSize(1);
    assertThat(logged).filteredOn(thrown -> thrown == failure).hasSize(1);
  }

  @Test
  void strangersAndVanishedNodesCannotBeEdited() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeRecord before = service.node(key).orElseThrow();

    assertThat(service.update(STRANGER, key, record -> record.toBuilder().priority(1).build())).isEmpty();
    assertThat(service.update(OWNER, new NodeKey(WORLD, 3, 64, 3, BlockFace.EAST), record -> record)).isEmpty();
    platform.ownsEverything = false;
    assertThat(service.update(OWNER, key, record -> record.toBuilder().priority(1).build())).isEmpty();

    assertThat(service.node(key)).contains(before);
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP)).isEqualTo(before);
  }

  @Test
  void anEditCannotChangeTheNodesOwnerTypeOrFace() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);

    assertThat(service.update(OWNER, key, record -> NodeRecord.builder(STRANGER, NodeType.GUARD, BlockFace.UP).build())).isEmpty();
    assertThat(service.update(OWNER, key, record -> NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build())).isEmpty();
    assertThat(service.node(key)).map(NodeRecord::owner).contains(OWNER);
    assertThat(service.node(key)).map(NodeRecord::type).contains(NodeType.GUARD);
  }

  @Test
  void anUnchangedEditWritesNothing() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    assertThat(service.update(OWNER, key, record -> record)).isEqualTo(service.node(key));

    verify(bundle.label(), never()).setText(anyString());
  }

  @Test
  void aNodeWaitingForPowerSkipsItsScanUntilTheAnchorIsPowered() {
    List<NodeScanPass> passes = new ArrayList<>();
    service.setScanHook(passes::add);
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    service.update(OWNER, key, record -> record.toBuilder().redstone(RedstoneMode.POWERED).build());

    platform.runScheduled();
    assertThat(passes).isEmpty();
    assertThat(platform.scheduled).hasSize(1);

    platform.powered.add("3,64,3");
    platform.runScheduled();
    assertThat(passes).hasSize(1);
    assertThat(passes.getFirst().window()).isEqualTo(new NodeScanCursor.Window(0, 64));
  }

  @Test
  void anUnpoweredModeNodeStopsScanningWhilePowered() {
    List<NodeScanPass> passes = new ArrayList<>();
    service.setScanHook(passes::add);
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    service.update(OWNER, key, record -> record.toBuilder().redstone(RedstoneMode.UNPOWERED).build());
    platform.powered.add("3,64,3");

    platform.runScheduled();
    assertThat(passes).isEmpty();

    platform.powered.clear();
    platform.runScheduled();
    assertThat(passes).hasSize(1);
  }

  @Test
  void anIgnoringNodeNeverReadsRedstonePower() {
    List<NodeScanPass> passes = new ArrayList<>();
    service.setScanHook(passes::add);
    placedGuard();

    platform.runScheduled();

    assertThat(passes).hasSize(1);
    assertThat(platform.powerReads).isZero();
  }

  @Test
  void aNodeSwitchedOffByRedstoneTellsTheHookOnEachPass() {
    List<NodeKey> inactive = new ArrayList<>();
    List<NodeScanPass> passes = new ArrayList<>();
    service.setScanHook(new NodeScanHook() {
      @Override
      public void scan(NodeScanPass pass) {
        passes.add(pass);
      }

      @Override
      public void inactive(NodeKey key) {
        inactive.add(key);
      }
    });
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    service.update(OWNER, key, record -> record.toBuilder().redstone(RedstoneMode.POWERED).build());

    platform.runScheduled();
    platform.runScheduled();
    assertThat(passes).isEmpty();
    assertThat(inactive).containsExactly(key, key);

    platform.powered.add("3,64,3");
    platform.runScheduled();
    assertThat(passes).hasSize(1);
    assertThat(inactive).hasSize(2);
  }

  @Test
  void unloadingAWorldTellsTheHook() {
    List<UUID> unloaded = new ArrayList<>();
    service.setScanHook(new NodeScanHook() {
      @Override
      public void scan(NodeScanPass pass) {
      }

      @Override
      public void worldUnloaded(UUID world) {
        unloaded.add(world);
      }
    });

    service.on(new WorldLifecycleEvent(world, WorldLifecycleEvent.Phase.UNLOAD));

    assertThat(unloaded).containsExactly(WORLD);
  }

  @Test
  void anUnavailableChunkStaysUnreadyAndRecoversOnItsRetry() {
    platform.chunkReadFailure = new IllegalStateException("region unavailable");
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isFalse();

    platform.chunkReadFailure = null;
    platform.runAsync();

    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isTrue();
    assertThat(platform.chunkReads).isEqualTo(2);
  }

  @Test
  void anUnavailableLoadsRetryCannotReplaceANewerGeneration() {
    platform.chunkReadFailure = new IllegalStateException("region unavailable");
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    platform.chunkReadFailure = null;
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, true));
    platform.runAsync();
    assertThat(platform.chunkReads).isEqualTo(1);
    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isTrue();
  }

  @Test
  void aFailedRefundRecoversAfterReloadWithoutExposingAnActiveNode() {
    PlayerRef owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    platform.saveFailure = new IllegalStateException("disk unavailable");
    service.remove(owner, key);
    platform.drainAsync();
    service.shutdown();
    platform.saveFailure = null;
    service = new NodeService(platform, NodeSettings::defaults, RevealSettings::defaults);

    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    platform.drainAsync();

    assertThat(service.node(key)).isEmpty();
    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
    verify(world).dropItemNaturally(any(Vec3.class), eq(nodeItems.get(NodeType.GUARD)));
  }

  @Test
  void aRejectedRefundHandoverRemainsPendingAndRetriesOnJoin() {
    PlayerRef owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    platform.refuseRunAt = true;
    service.remove(owner, key);
    platform.drainAsync();
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP).data()).containsEntry("adapt:return", "pending");
    verify(owner.inventory(), never()).addItem(any(ItemRef.class));

    platform.refuseRunAt = false;
    service.on(new PlayerJoinEvent(owner));
    platform.drainAsync();

    verify(owner.inventory()).addItem(nodeItems.get(NodeType.GUARD));
    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
  }

  @Test
  void unloadingTheWorldPausesAReturnBeforeItTouchesRetiredStorage() {
    PlayerRef owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    platform.deferAsync = true;
    service.remove(owner, key);
    platform.worlds.remove(WORLD);
    platform.saveFailure = new IllegalStateException("world is no longer loaded");

    platform.drainAsync();

    assertThat(platform.events).isEmpty();
    assertThat(logged).noneMatch(thrown -> "world is no longer loaded".equals(thrown.getMessage()));
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP).data()).containsEntry("adapt:return", "pending");
    platform.saveFailure = null;
    platform.worlds.put(WORLD, world);
    service.onTick();
    platform.drainAsync();
    verify(owner.inventory()).addItem(nodeItems.get(NodeType.GUARD));
  }

  @Test
  void anAmbiguousRefundIsNotReplayedAfterRestartUntilResolved() {
    PlayerRef owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    PlayerInventoryRef inventory = owner.inventory();
    doThrow(new IllegalStateException("inventory failure")).when(inventory).addItem(any(ItemRef.class));
    service.remove(owner, key);
    platform.drainAsync();
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP).data()).containsEntry("adapt:return", "delivering");
    service.shutdown();
    service = new NodeService(platform, NodeSettings::defaults, RevealSettings::defaults);
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
    platform.drainAsync();
    verify(owner.inventory(), times(1)).addItem(nodeItems.get(NodeType.GUARD));
    assertThat(service.node(key)).isEmpty();

    assertThat(service.resolveReturn(STRANGER, key, true)).isFalse();
    assertThat(service.resolveReturn(OWNER, key, true)).isTrue();
    platform.drainAsync();
    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
  }

  @Test
  void editingTheWorkAreaKeepsKnownTargetsInWorldCoordinates() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    List<NodeScanPass> passes = new ArrayList<>();
    service.setScanHook(pass -> {
      passes.add(pass);
      if (passes.size() == 1) {
        pass.discovery().remember(20, true);
      }
    });
    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeRecord original = service.node(key).orElseThrow();
    int[] target = NodeArea.position(key, original, 20);
    platform.runScheduled();

    service.update(OWNER, key, record -> record.toBuilder().area(NodeCuboid.symmetric(7, 7, 7)).build());
    platform.runScheduled();

    assertThat(passes.subList(1, passes.size())).anySatisfy(pass -> {
      assertThat(IntStream.of(pass.window().indices()).anyMatch(index -> Arrays.equals(NodeArea.position(key, pass.record(), index), target))).isTrue();
    });
    assertThat(passes.getFirst().current().getAsBoolean()).isFalse();
  }

  @Test
  void itemSpawnsWakeOnlyCollectionQueriesInsideTheNodeArea() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.COLLECTION, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    service.emptyQuery(key, service.node(key).orElseThrow(), platform.now);
    assertThat(service.queryDue(key, platform.now + 1)).isFalse();
    ItemSpawnEvent outside = mock(ItemSpawnEvent.class);
    when(outside.location()).thenReturn(new Loc(world, 100D, 65D, 100D));
    service.on(outside);
    assertThat(service.queryDue(key, platform.now + 1)).isFalse();

    ItemSpawnEvent inside = mock(ItemSpawnEvent.class);
    when(inside.location()).thenReturn(new Loc(world, 3.5D, 65D, 3.5D));
    service.on(inside);
    assertThat(service.queryDue(key, platform.now + 1)).isTrue();
  }

  @Test
  void animalSpawnsWakeEmptyPasturesBeforeTheTenSecondReconciliation() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.PASTURE, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    service.emptyQuery(key, service.node(key).orElseThrow(), platform.now);
    assertThat(service.queryDue(key, platform.now + 9_999)).isFalse();
    assertThat(service.queryDue(key, platform.now + 10_000)).isTrue();
    service.emptyQuery(key, service.node(key).orElseThrow(), platform.now);
    CreatureSpawnEvent spawn = mock(CreatureSpawnEvent.class);
    LivingRef cow = mock(LivingRef.class);
    when(cow.has(EntityTrait.ANIMAL)).thenReturn(true);
    when(spawn.entity()).thenReturn(cow);
    when(spawn.location()).thenReturn(new Loc(world, 3.5D, 65D, 3.5D));

    service.on(spawn);

    assertThat(service.queryDue(key, platform.now + 1)).isTrue();
  }

  private PlayerRef placedGuard() {
    readyChunk();
    blocks.put("3,64,3", Id.minecraft("stone"));
    onlineRoster(OWNER);
    PlayerRef owner = player(OWNER);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);
    return owner;
  }

  private void readyChunk() {
    service.on(new ChunkEvent(world, chunk(0, 0), ChunkEvent.Phase.LOAD, false));
  }

  private GolemRoster onlineRoster(UUID owner) {
    GolemRoster roster = new GolemRoster();
    platform.rosters.put(owner, roster);
    platform.online.add(owner);
    return roster;
  }

  private PlayerRef player(UUID id) {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
    lenient().when(player.world()).thenReturn(world);
    lenient().when(player.location()).thenReturn(new Loc(world, 0D, 64D, 0D));
    return player;
  }

  private static ChunkPos chunk(int x, int z) {
    return new ChunkPos(x, z);
  }

  private BlockRef block(int x, int y, int z) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.y()).thenReturn(y);
    lenient().when(block.z()).thenReturn(z);
    lenient().when(block.type()).thenAnswer(invocation -> blocks.getOrDefault(x + "," + y + "," + z, Id.minecraft("air")));
    lenient().when(block.location()).thenReturn(new Loc(world, x, y, z));
    lenient().when(block.relative(any(BlockFace.class))).thenAnswer(invocation -> {
      BlockFace face = invocation.getArgument(0);
      return block(x + face.dx(), y + face.dy(), z + face.dz());
    });
    return block;
  }

  private <T extends EntityRef> T entity(Class<T> type) {
    T entity = mock(type);
    AtomicBoolean valid = new AtomicBoolean(true);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.isValid()).thenAnswer(invocation -> valid.get());
    lenient().doAnswer(invocation -> {
      valid.set(false);
      return null;
    }).when(entity).remove();
    if (entity instanceof TextDisplayRef text) {
      lenient().when(text.transform()).thenReturn(Transform.IDENTITY);
    }
    spawned.add(entity);
    return entity;
  }

  private static final class FakePolicy implements NodeWeavingPolicy {
    private int level = 1;
    private int nodeCap = 4;
    private int maxAreaRadius = 2;
    private boolean canPlace = true;
    private boolean canAccessContainer = true;

    @Override
    public int level(PlayerRef player) {
      return level;
    }

    @Override
    public int nodeCap(int level) {
      return nodeCap;
    }

    @Override
    public int maxAreaRadius(int level) {
      return maxAreaRadius;
    }

    @Override
    public int filterSlots(int level) {
      return 3;
    }

    @Override
    public boolean canPlace(PlayerRef player, Loc anchor, Loc placement) {
      return canPlace;
    }

    @Override
    public boolean canAccessContainer(PlayerRef player, Loc container) {
      return canAccessContainer;
    }

    @Override
    public boolean crookOnlyReveal(PlayerRef viewer) {
      return false;
    }

    @Override
    public boolean hidesOutline(PlayerRef viewer) {
      return false;
    }
  }
}
