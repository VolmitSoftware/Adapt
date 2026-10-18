package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import art.arcane.adapt.util.common.scheduling.J;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
  private final Map<String, Material> blocks = new HashMap<>();
  private final Map<NodeType, ItemStack> nodeItems = new EnumMap<>(NodeType.class);
  private final List<Entity> spawned = new ArrayList<>();
  private final List<LogRecord> logged = new ArrayList<>();
  private final Handler capture = new Handler() {
    @Override
    public void publish(LogRecord record) {
      logged.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  };
  private MockedStatic<HiddenDisplays> displays;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private MockedStatic<ComponentLogger> componentLogger;
  private RevealService reveal;
  private World world;
  private NodeService service;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    lenient().when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> block(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    platform.worlds.put(WORLD, world);
    displays = mockStatic(HiddenDisplays.class);
    displays.when(() -> HiddenDisplays.spawnFaceItem(any(Block.class), any(BlockFace.class), any(ItemStack.class), anyFloat(), any(Vector3f.class)))
        .thenAnswer(invocation -> entity(ItemDisplay.class));
    displays.when(() -> HiddenDisplays.spawnFaceHitbox(any(Block.class), any(BlockFace.class), anyFloat(), anyFloat()))
        .thenAnswer(invocation -> entity(Interaction.class));
    displays.when(() -> HiddenDisplays.spawnLabel(any(Location.class), any(Component.class), any(Color.class), anyFloat()))
        .thenAnswer(invocation -> entity(TextDisplay.class));
    items = mockStatic(GolemItems.class);
    for (NodeType type : NodeType.values()) {
      ItemStack item = mock(ItemStack.class);
      nodeItems.put(type, item);
      items.when(() -> GolemItems.node(type)).thenReturn(item);
    }
    reveal = mock(RevealService.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.of(reveal));
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    lenient().when(plugin.isEnabled()).thenReturn(true);
    componentLogger = mockStatic(ComponentLogger.class);
    componentLogger.when(ComponentLogger::logger).thenReturn(null);
    Logger.getLogger("AdaptTest").addHandler(capture);
    service = new NodeService(platform, NodeSettings::defaults, RevealSettings::defaults);
  }

  @AfterEach
  void tearDown() {
    Logger.getLogger("AdaptTest").removeHandler(capture);
    componentLogger.close();
    scheduling.close();
    runtime.close();
    items.close();
    displays.close();
  }

  @Test
  void loadingAChunkIndexesValidNodesAndDropsOnesWhoseAnchorIsGone() {
    blocks.put("1,64,1", Material.STONE);
    blocks.put("2,64,1", Material.STONE);
    NodeRecord guard = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    NodeRecord deposit = NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.NORTH).build();
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(guard));
    platform.put(WORLD, 2, 64, 1, NodeFaces.EMPTY.with(deposit));
    GolemRoster roster = onlineRoster(OWNER);
    roster.addNode(GolemRoster.NodeEntry.at(GolemState.Anchor.of(WORLD, 2, 64, 1), BlockFace.NORTH, NodeType.DEPOSIT));

    service.on(new ChunkLoadEvent(chunk(0, 0), false));

    NodeKey guardKey = new NodeKey(WORLD, 1, 64, 1, BlockFace.UP);
    NodeKey depositKey = new NodeKey(WORLD, 2, 64, 1, BlockFace.NORTH);
    assertThat(service.node(guardKey)).contains(guard);
    assertThat(service.node(depositKey)).isEmpty();
    assertThat(platform.stored(WORLD, 2, 64, 1).isEmpty()).isTrue();
    assertThat(roster.nodeCount()).isZero();
    verify(world).dropItemNaturally(any(Location.class), eq(nodeItems.get(NodeType.DEPOSIT)));
    assertThat(service.renderer().bundle(guardKey)).isPresent();
    assertThat(service.renderer().bundle(depositKey)).isEmpty();
    verify(reveal).nodeSpawned(guardKey);
  }

  @Test
  void placementWritesTheRecordAndRosterEntryThenRendersAndSchedulesAScan() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    GolemRoster roster = onlineRoster(OWNER);

    NodePlacementVerdict verdict = service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    assertThat(verdict).isEqualTo(NodePlacementVerdict.READY);
    NodeRecord stored = platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP);
    assertThat(stored.owner()).isEqualTo(OWNER);
    assertThat(stored.type()).isEqualTo(NodeType.GUARD);
    assertThat(stored.radiusX()).isEqualTo(2);
    assertThat(service.node(key)).contains(stored);
    assertThat(roster.nodes()).extracting(GolemRoster.NodeEntry::type).containsExactly(NodeType.GUARD);
    assertThat(platform.rosterSaves).containsEntry(OWNER, 1);
    assertThat(service.renderer().bundle(key)).isPresent();
    assertThat(platform.scheduled).hasSize(1);
  }

  @Test
  void theDefaultAreaNeverExceedsTheOwnersRadiusCap() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    onlineRoster(OWNER);
    policy.maxAreaRadius = 1;

    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);

    NodeRecord stored = platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP);
    assertThat(List.of(stored.radiusX(), stored.radiusY(), stored.radiusZ())).containsOnly(1);
  }

  @Test
  void placementIsRefusedOnAnOccupiedFaceAtTheCapAndOnNonContainers() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    GolemRoster roster = onlineRoster(OWNER);
    Player owner = player(OWNER);
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
    blocks.put("3,64,3", Material.STONE);
    GolemRoster roster = onlineRoster(OWNER);
    Player owner = player(OWNER);

    assertThat(service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.FISHING, policy)).isEqualTo(NodePlacementVerdict.NEEDS_SIDE_FACE);
    assertThat(service.place(owner, block(3, 64, 3), BlockFace.DOWN, NodeType.FISHING, policy)).isEqualTo(NodePlacementVerdict.NEEDS_SIDE_FACE);
    assertThat(roster.nodeCount()).isZero();
    assertThat(service.place(owner, block(3, 64, 3), BlockFace.NORTH, NodeType.FISHING, policy)).isEqualTo(NodePlacementVerdict.READY);
    assertThat(roster.nodeCount()).isEqualTo(1);
  }

  @Test
  void containerNodesNeedChestAccess() {
    readyChunk();
    blocks.put("3,64,3", Material.CHEST);
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
    blocks.put("3,64,3", Material.STONE);

    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy)).isEqualTo(NodePlacementVerdict.UNAVAILABLE);
    onlineRoster(OWNER);
    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy)).isEqualTo(NodePlacementVerdict.NOT_LOADED);
  }

  @Test
  void aNodeOutsideTheCurrentRegionIsNeitherPlacedNorRemoved() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    GolemRoster roster = onlineRoster(OWNER);
    Player owner = player(OWNER);
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
    blocks.put("3,64,3", Material.STONE);
    onlineRoster(OWNER);
    onlineRoster(STRANGER);
    service.place(player(STRANGER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    assertThat(service.place(player(OWNER), block(3, 64, 3), BlockFace.EAST, NodeType.GUARD, policy)).isEqualTo(NodePlacementVerdict.CHUNK_FULL);
  }

  @Test
  void theOwnerGetsTheItemBackWhenRemovingANodeAndStrangersCannotRemoveIt() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    GolemRoster roster = onlineRoster(OWNER);
    Player owner = player(OWNER);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    assertThat(service.remove(player(STRANGER), key)).isFalse();
    assertThat(service.remove(owner, key)).isTrue();

    assertThat(service.node(key)).isEmpty();
    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
    assertThat(roster.nodeCount()).isZero();
    verify(owner.getInventory()).addItem(nodeItems.get(NodeType.GUARD));
    verify(bundle.icon()).remove();
    verify(bundle.hitbox()).remove();
    verify(bundle.label()).remove();
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    verify(reveal).nodeGone(key);
  }

  @Test
  void aScanDropsANodeWhoseAnchorBrokeSinceTheLastPassAndOtherwiseReschedules() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    GolemRoster roster = onlineRoster(OWNER);
    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);

    platform.runScheduled();
    assertThat(platform.scheduled).hasSize(1);
    assertThat(platform.scheduled.getFirst().delayTicks()).isEqualTo(NodeSettings.defaults().scanIntervalTicks());

    blocks.put("3,64,3", Material.AIR);
    platform.runScheduled();

    assertThat(service.node(key)).isEmpty();
    assertThat(platform.scheduled).isEmpty();
    assertThat(roster.nodeCount()).isZero();
    verify(world).dropItemNaturally(any(Location.class), eq(nodeItems.get(NodeType.GUARD)));
  }

  @Test
  void anOldScanChainStopsWhenTheNodeIsReplacedAtTheSameFace() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    onlineRoster(OWNER);
    Player owner = player(OWNER);
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
    blocks.put("3,64,3", Material.STONE);
    onlineRoster(OWNER);
    List<NodeScanPass> passes = new ArrayList<>();
    service.setScanHook(passes::add);
    policy.maxAreaRadius = 2;
    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.HARVEST, policy);

    platform.runScheduled();
    platform.runScheduled();

    assertThat(passes).hasSize(2);
    assertThat(passes.get(0).window()).isEqualTo(new NodeScanCursor.Window(0, 64));
    assertThat(passes.get(1).window()).isEqualTo(new NodeScanCursor.Window(64, 61));
    assertThat(passes.get(0).maxOpenTasks()).isEqualTo(16);
  }

  @Test
  void unloadingTheChunkDespawnsBundlesAndForgetsTheNodes() {
    blocks.put("1,64,1", Material.STONE);
    NodeRecord guard = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(guard));
    service.on(new ChunkLoadEvent(chunk(0, 0), false));
    NodeKey key = new NodeKey(WORLD, 1, 64, 1, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    service.on(new ChunkUnloadEvent(chunk(0, 0)));

    assertThat(service.node(key)).isEmpty();
    assertThat(service.renderer().bundle(key)).isEmpty();
    assertThat(service.renderer().nodeOf(bundle.hitbox())).isEmpty();
    verify(bundle.icon()).remove();
    verify(reveal).nodeGone(key);
    assertThat(platform.stored(WORLD, 1, 64, 1).get(BlockFace.UP)).isEqualTo(guard);
  }

  @Test
  void bundleEntitiesMapBackToTheirNode() {
    blocks.put("1,64,1", Material.STONE);
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build()));
    service.on(new ChunkLoadEvent(chunk(0, 0), false));
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
    roster.addNode(GolemRoster.NodeEntry.at(GolemState.Anchor.of(WORLD, 5, 64, 5), BlockFace.UP, NodeType.GUARD));
    roster.addNode(GolemRoster.NodeEntry.at(GolemState.Anchor.of(WORLD, 6, 64, 6), BlockFace.UP, NodeType.GUARD));
    roster.addNode(GolemRoster.NodeEntry.at(GolemState.Anchor.of(WORLD, 5, 64, 5), BlockFace.NORTH, NodeType.DEPOSIT));
    Player joined = player(OWNER);

    service.on(new PlayerJoinEvent(joined, (Component) null));
    platform.runAsync();

    assertThat(roster.nodes()).extracting(GolemRoster.NodeEntry::face).containsExactly(BlockFace.UP);
    assertThat(roster.nodes().getFirst().block().x()).isEqualTo(5);
    assertThat(platform.rosterSaves).containsEntry(OWNER, 1);
  }

  @Test
  void aRemovedNodesItemIsHandedOutOnlyAfterItsPlateIsSaved() {
    Player owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    platform.deferAsync = true;

    assertThat(service.remove(owner, key)).isTrue();

    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
    verify(owner.getInventory(), never()).addItem(any(ItemStack[].class));
    platform.runAsync();
    assertThat(platform.events).containsExactly("save 3,3");
    verify(owner.getInventory()).addItem(nodeItems.get(NodeType.GUARD));
  }

  @Test
  void aFailedSaveLogsItsStackTraceAndWithholdsTheItem() {
    Player owner = placedGuard();
    platform.saveFailure = new IllegalStateException("disk full");

    assertThat(service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP))).isTrue();

    verify(owner.getInventory(), never()).addItem(any(ItemStack[].class));
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    assertThat(logged).anySatisfy(record -> assertThat(record.getThrown()).hasMessage("disk full"));
    assertThat(platform.stored(WORLD, 3, 64, 3).isEmpty()).isTrue();
  }

  @Test
  void aPlateThatStaysBusyIsRetriedThenGivenUpLoudlyWithoutAnItem() {
    Player owner = placedGuard();
    platform.busySaves = 1000;

    service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    platform.drainAsync();

    assertThat(platform.events).hasSize(20);
    verify(owner.getInventory(), never()).addItem(any(ItemStack[].class));
    assertThat(logged).anySatisfy(record -> assertThat(record.getThrown()).hasMessageContaining("busy"));
  }

  @Test
  void aPlateThatFreesUpIsSavedAndTheItemHandedOutOnce() {
    Player owner = placedGuard();
    platform.busySaves = 2;

    service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    platform.drainAsync();

    assertThat(platform.events).hasSize(3);
    verify(owner.getInventory()).addItem(nodeItems.get(NodeType.GUARD));
  }

  @Test
  void anOwnerWhoLeftTheRegionGetsTheItemDroppedAtTheNode() {
    Player owner = placedGuard();
    platform.deferAsync = true;
    service.remove(owner, new NodeKey(WORLD, 3, 64, 3, BlockFace.UP));
    when(owner.isOnline()).thenReturn(false);

    platform.runAsync();

    verify(owner.getInventory(), never()).addItem(any(ItemStack[].class));
    verify(world).dropItemNaturally(any(Location.class), eq(nodeItems.get(NodeType.GUARD)));
  }

  @Test
  void aBrokenAnchorDropsItsItemOnlyAfterTheSave() {
    placedGuard();
    blocks.put("3,64,3", Material.AIR);
    platform.deferAsync = true;

    platform.runScheduled();

    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    platform.runAsync();
    verify(world).dropItemNaturally(any(Location.class), eq(nodeItems.get(NodeType.GUARD)));
  }

  @Test
  void aNodeWhoseStoredRecordIsAlreadyGoneYieldsNoItem() {
    Player owner = placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    platform.store.clear();

    assertThat(service.remove(owner, key)).isFalse();

    assertThat(service.node(key)).isEmpty();
    assertThat(platform.events).isEmpty();
    verify(owner.getInventory(), never()).addItem(any(ItemStack[].class));
  }

  @Test
  void aBrokenAnchorWhoseRecordIsAlreadyGoneDropsNothing() {
    placedGuard();
    platform.store.clear();
    blocks.put("3,64,3", Material.AIR);

    platform.runScheduled();

    assertThat(platform.events).isEmpty();
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void aFailureAfterTheNodeIsStoredStillReportsReadySoTheItemIsConsumed() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    onlineRoster(OWNER);
    displays.when(() -> HiddenDisplays.spawnFaceItem(any(Block.class), any(BlockFace.class), any(ItemStack.class), anyFloat(), any(Vector3f.class)))
        .thenThrow(new IllegalStateException("display failed"));

    NodePlacementVerdict verdict = service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    assertThat(verdict).isEqualTo(NodePlacementVerdict.READY);
    assertThat(platform.stored(WORLD, 3, 64, 3).get(BlockFace.UP)).isNotNull();
    assertThat(logged).anySatisfy(record -> assertThat(record.getThrown()).hasMessage("display failed"));
  }

  @Test
  void placementReplacesAStaleRosterEntryOnTheSameFace() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    GolemRoster roster = onlineRoster(OWNER);
    roster.addNode(GolemRoster.NodeEntry.at(GolemState.Anchor.of(WORLD, 3, 64, 3), BlockFace.UP, NodeType.HARVEST));

    service.place(player(OWNER), block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);

    assertThat(roster.nodes()).extracting(GolemRoster.NodeEntry::type).containsExactly(NodeType.GUARD);
  }

  @Test
  void newChunksAreReadyWithoutReadingTheMantle() {
    service.on(new ChunkLoadEvent(chunk(0, 0), true));

    assertThat(platform.chunkReads).isZero();
    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isTrue();
  }

  @Test
  void anEmptyChunkReadCompletesWithoutHoppingToTheRegion() {
    platform.refuseRunAt = true;

    service.on(new ChunkLoadEvent(chunk(0, 0), false));

    assertThat(platform.chunkReads).isEqualTo(1);
    assertThat(service.index().isReady(WORLD, GolemRoster.chunkKey(0, 0))).isTrue();
  }

  @Test
  void aRefusedRegionHopOnlyForgetsItsOwnLoad() {
    blocks.put("1,64,1", Material.STONE);
    NodeRecord guard = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    platform.put(WORLD, 1, 64, 1, NodeFaces.EMPTY.with(guard));
    platform.deferAsync = true;
    service.on(new ChunkLoadEvent(chunk(0, 0), false));
    service.on(new ChunkLoadEvent(chunk(0, 0), false));
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
    verify(bundle.label()).text(any(Component.class));
    verify(bundle.icon()).setGlowColorOverride(DyeColor.RED.getColor());
    verify(bundle.icon(), atLeastOnce()).setGlowing(true);
  }

  @Test
  void statusChangesRedrawTheLabelOnceAndSurviveAnEdit() {
    placedGuard();
    NodeKey key = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeBundle bundle = service.renderer().bundle(key).orElseThrow();

    service.status(key, NodeStatus.WORKING);
    service.status(key, NodeStatus.WORKING);

    verify(bundle.label(), times(1)).text(any(Component.class));
    assertThat(service.renderer().status(key)).contains(NodeStatus.WORKING);

    service.update(OWNER, key, record -> record.toBuilder().priority(5).build());

    assertThat(service.renderer().status(key)).contains(NodeStatus.WORKING);
    service.status(new NodeKey(WORLD, 3, 64, 3, BlockFace.EAST), NodeStatus.FULL);
    assertThat(service.renderer().status(new NodeKey(WORLD, 3, 64, 3, BlockFace.EAST))).isEmpty();
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

    verify(bundle.label(), never()).text(any(Component.class));
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

    service.on(new WorldUnloadEvent(world));

    assertThat(unloaded).containsExactly(WORLD);
  }

  private Player placedGuard() {
    readyChunk();
    blocks.put("3,64,3", Material.STONE);
    onlineRoster(OWNER);
    Player owner = player(OWNER);
    service.place(owner, block(3, 64, 3), BlockFace.UP, NodeType.GUARD, policy);
    return owner;
  }

  private void readyChunk() {
    service.on(new ChunkLoadEvent(chunk(0, 0), false));
  }

  private GolemRoster onlineRoster(UUID owner) {
    GolemRoster roster = new GolemRoster();
    platform.rosters.put(owner, roster);
    platform.online.add(owner);
    return roster;
  }

  private Player player(UUID id) {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    lenient().when(player.getUniqueId()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
    lenient().when(player.getWorld()).thenReturn(world);
    lenient().when(player.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    return player;
  }

  private Chunk chunk(int x, int z) {
    Chunk chunk = mock(Chunk.class);
    lenient().when(chunk.getWorld()).thenReturn(world);
    lenient().when(chunk.getX()).thenReturn(x);
    lenient().when(chunk.getZ()).thenReturn(z);
    return chunk;
  }

  private Block block(int x, int y, int z) {
    Block block = mock(Block.class);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    lenient().when(block.getType()).thenAnswer(invocation -> blocks.getOrDefault(x + "," + y + "," + z, Material.AIR));
    lenient().when(block.getLocation()).thenReturn(new Location(world, x, y, z));
    lenient().when(block.getRelative(any(BlockFace.class))).thenAnswer(invocation -> {
      BlockFace face = invocation.getArgument(0);
      return block(x + face.getModX(), y + face.getModY(), z + face.getModZ());
    });
    return block;
  }

  private <T extends Entity> T entity(Class<T> type) {
    T entity = mock(type);
    AtomicBoolean valid = new AtomicBoolean(true);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(entity.isValid()).thenAnswer(invocation -> valid.get());
    lenient().doAnswer(invocation -> {
      valid.set(false);
      return null;
    }).when(entity).remove();
    if (entity instanceof TextDisplay text) {
      lenient().when(text.getTransformation())
          .thenReturn(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1F, 1F, 1F), new Quaternionf()));
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
    public int level(Player player) {
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
    public boolean canPlace(Player player, Location anchor, Location placement) {
      return canPlace;
    }

    @Override
    public boolean canAccessContainer(Player player, Location container) {
      return canAccessContainer;
    }
  }
}
