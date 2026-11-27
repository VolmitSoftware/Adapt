package art.arcane.adapt.content.golem.reveal;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.golem.HiddenDisplays;
import art.arcane.adapt.engine.fx.ViewerDisplayPort;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemVitals;
import art.arcane.adapt.content.golem.GolemWorkStatus;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeBundle;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeOutline;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeRenderer;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.node.NodeSettings;
import art.arcane.adapt.content.golem.node.NodeVisibility;
import art.arcane.adapt.content.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevealServiceTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

  private final List<TextDisplayRef> spawned = new ArrayList<>();
  private final AtomicBoolean schedulerAccepts = new AtomicBoolean(true);
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<HiddenDisplays> displays;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<NodeOutline> outlineBlocks;
  private WorldRef world;
  private PlayerRef viewer;
  private GolemRegistry registry;
  private ViewerDisplayPort outlines;

  @BeforeEach
  void setUp() {
    outlineBlocks = mockStatic(NodeOutline.class, CALLS_REAL_METHODS);
    outlineBlocks.when(() -> NodeOutline.glass(nullable(DyeColor.class))).thenReturn(mock(BlockStateRef.class));
    outlines = mock(ViewerDisplayPort.class);
    AdaptPorts.install(ViewerDisplayPort.class, outlines);
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> runIfAccepted(invocation.getArgument(1)));
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), anyLong())).thenAnswer(invocation -> runIfAccepted(invocation.getArgument(1)));
    when(adaptScheduler.isRegionized()).thenReturn(false);
    when(adaptScheduler.owns(any(EntityRef.class))).thenReturn(true);
    displays = mockStatic(HiddenDisplays.class);
    displays.when(() -> HiddenDisplays.spawnLabel(any(Loc.class), anyString(), anyInt(), anyFloat()))
        .thenAnswer(invocation -> spawnDisplay());
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
    registry = mock(GolemRegistry.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(100D);
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    viewer = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(inventory.mainHand()).thenReturn(mock(ItemRef.class));
    lenient().when(inventory.offHand()).thenReturn(mock(ItemRef.class));
    lenient().when(viewer.uuid()).thenReturn(OWNER);
    lenient().when(viewer.isOnline()).thenReturn(true);
    lenient().when(viewer.inventory()).thenReturn(inventory);
    lenient().when(viewer.location()).thenAnswer(invocation -> new Loc(world, 0D, 64D, 0D));
    lenient().when(viewer.eyeLocation()).thenAnswer(invocation -> new Loc(world, 0D, 65.6D, 0D));
    lenient().when(viewer.world()).thenReturn(world);
  }

  @AfterEach
  void tearDown() {
    AdaptPorts.uninstall(ViewerDisplayPort.class);
    outlineBlocks.close();
    vitals.close();
    runtime.close();
    items.close();
    displays.close();
    scheduling.close();
  }

  @Test
  void shutdownRemovesLabelsEvenAfterThePluginIsDisabled() {
    RevealService service = new RevealService(RevealSettings::defaults);
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    GolemHandle golem = golem(distance);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    service.update(viewer);
    assertThat(spawned).hasSize(1);
    TextDisplayRef label = spawned.getFirst();
    displays.verify(() -> HiddenDisplays.show(viewer, label));

    TestCores.clear();
    schedulerAccepts.set(false);
    service.shutdown();

    verify(label).remove();
  }

  @Test
  void aShownGolemWhoseLabelVanishedGetsANewOneOnTheNextPoll() {
    RevealService service = new RevealService(RevealSettings::defaults);
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    GolemHandle golem = golem(distance);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    service.update(viewer);
    TextDisplayRef first = spawned.getFirst();
    when(first.isValid()).thenReturn(false);

    service.update(viewer);

    assertThat(spawned).hasSize(2);
    displays.verify(() -> HiddenDisplays.show(viewer, spawned.get(1)));
  }

  @Test
  void aGolemThatRefusesItsLabelIsNotRetriedEveryPoll() {
    RevealService service = new RevealService(RevealSettings::defaults);
    GolemHandle golem = golem(new AtomicReference<>(5D));
    when(golem.entity().addPassenger(any(EntityRef.class))).thenReturn(false);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));

    service.update(viewer);
    service.update(viewer);

    assertThat(spawned).hasSize(1);
    verify(spawned.getFirst()).remove();
  }

  @Test
  void golemsLeaveTwoBlocksPastTheRevealRangeAndReenterOnlyInsideIt() {
    RevealService service = new RevealService(RevealSettings::defaults);
    AtomicReference<Double> distance = new AtomicReference<>(31D);
    GolemHandle golem = golem(distance);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();

    distance.set(33.5D);
    service.update(viewer);
    displays.verify(() -> HiddenDisplays.hide(viewer, label), never());
    verify(label, never()).remove();

    distance.set(34.5D);
    service.update(viewer);
    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();

    distance.set(33D);
    service.update(viewer);
    assertThat(spawned).hasSize(1);

    distance.set(32D);
    service.update(viewer);
    assertThat(spawned).hasSize(2);
  }

  @Test
  void ownersSeeTheirNodeBundlesAndOtherGolemancersOnlyWhenVisibilityAllowsIt() {
    NodeKey key = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
    NodeBundle bundle = nodeBundle(key, OWNER);
    PlayerRef stranger = viewer(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    runtime.when(GolemancyRuntime::nodeSettings).thenReturn(NodeSettings.defaults());
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.level(stranger)).thenReturn(1);
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);
    service.update(stranger);

    for (EntityRef entity : bundle.entities()) {
      displays.verify(() -> HiddenDisplays.show(viewer, entity));
      displays.verify(() -> HiddenDisplays.show(stranger, entity), never());
    }

    runtime.when(GolemancyRuntime::nodeSettings).thenReturn(new NodeSettings(16, 40, 64, 16, NodeVisibility.ANY_GOLEMANCER));
    service.update(stranger);

    for (EntityRef entity : bundle.entities()) {
      displays.verify(() -> HiddenDisplays.show(stranger, entity));
    }
  }

  @Test
  void puttingTheRevealItemAwayHidesNodesAndAGoneNodeIsForgotten() {
    NodeKey key = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
    NodeBundle bundle = nodeBundle(key, OWNER);
    runtime.when(GolemancyRuntime::nodeSettings).thenReturn(NodeSettings.defaults());
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);

    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.empty());
    service.update(viewer);

    for (EntityRef entity : bundle.entities()) {
      displays.verify(() -> HiddenDisplays.hide(viewer, entity));
    }

    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.of(GolemItemKind.NODE));
    service.update(viewer);
    service.nodeGone(key);
    service.update(viewer);

    for (EntityRef entity : bundle.entities()) {
      displays.verify(() -> HiddenDisplays.show(viewer, entity), times(3));
    }
  }

  @Test
  void aReplacementBundleUnderTheSameKeyIsShownToArmedViewers() {
    NodeKey key = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
    nodeBundle(key, OWNER);
    runtime.when(GolemancyRuntime::nodeSettings).thenReturn(NodeSettings.defaults());
    lenient().when(viewer.world()).thenReturn(world);
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    NodeBundle replacement = bundle();
    NodeRenderer renderer = GolemancyRuntime.nodes().orElseThrow().renderer();
    when(renderer.bundle(key)).thenReturn(Optional.of(replacement));

    when(core.getPlatform().host().player(OWNER)).thenReturn(viewer);
    service.nodeSpawned(key);

    for (EntityRef entity : replacement.entities()) {
      displays.verify(() -> HiddenDisplays.show(viewer, entity));
    }
  }

  @Test
  void aCrookViewerSeesNearbyOwnedAreasUntilTheyPutTheCrookAway() {
    NodeKey key = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
    nodeBundle(key, OWNER);
    runtime.when(GolemancyRuntime::nodeSettings).thenReturn(NodeSettings.defaults());
    lenient().when(viewer.world()).thenReturn(world);
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);
    verify(outlines, times(2)).showCuboid(eq(NodeOutline.CHANNEL), anyString(), eq(viewer), any(Loc.class), any(Vec3.class),
        any(BlockStateRef.class), isNull(), anyBoolean(), eq(NodeOutline.DURATION_TICKS));

    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.empty());
    service.update(viewer);
    verify(outlines).clearViewer(NodeOutline.CHANNEL, OWNER);
  }

  @Test
  void anOwnerWhoHidesStatusLabelsGetsNoLabelAndLosesAShownOne() {
    RevealService service = new RevealService(RevealSettings::defaults);
    GolemHandle golem = golem(new AtomicReference<>(5D));
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    GolemLabelPolicy labels = mock(GolemLabelPolicy.class);
    when(labels.hidesLabels(viewer)).thenReturn(true);
    runtime.when(GolemancyRuntime::labelPolicy).thenReturn(Optional.of(labels));

    service.update(viewer);
    assertThat(spawned).isEmpty();

    when(labels.hidesLabels(viewer)).thenReturn(false);
    service.update(viewer);
    assertThat(spawned).hasSize(1);
    TextDisplayRef label = spawned.getFirst();
    displays.verify(() -> HiddenDisplays.show(viewer, label));

    when(labels.hidesLabels(viewer)).thenReturn(true);
    service.update(viewer);
    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    assertThat(spawned).hasSize(1);
  }

  @Test
  void crookOnlyRevealShowsNodesOnlyWhileTheViewerHoldsTheCrook() {
    NodeKey key = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
    NodeBundle bundle = nodeBundle(key, OWNER);
    runtime.when(GolemancyRuntime::nodeSettings).thenReturn(NodeSettings.defaults());
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.crookOnlyReveal(viewer)).thenReturn(true);
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.of(GolemItemKind.NODE));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    for (EntityRef entity : bundle.entities()) {
      displays.verify(() -> HiddenDisplays.show(viewer, entity), never());
    }

    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
    service.update(viewer);

    for (EntityRef entity : bundle.entities()) {
      displays.verify(() -> HiddenDisplays.show(viewer, entity));
    }
  }

  @Test
  void aViewerWhoSwitchesTheWorkAreaOutlineOffSeesNoOutline() {
    NodeKey key = new NodeKey(WORLD, 4, 64, 0, BlockFace.UP);
    nodeBundle(key, OWNER);
    runtime.when(GolemancyRuntime::nodeSettings).thenReturn(NodeSettings.defaults());
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.hidesOutline(viewer)).thenReturn(true);
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    lenient().when(viewer.world()).thenReturn(world);
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);
    verify(outlines, never()).showCuboid(eq(NodeOutline.CHANNEL), anyString(), eq(viewer), any(Loc.class), any(Vec3.class),
        any(BlockStateRef.class), isNull(), anyBoolean(), eq(NodeOutline.DURATION_TICKS));

    when(policy.hidesOutline(viewer)).thenReturn(false);
    service.update(viewer);
    verify(outlines, times(2)).showCuboid(eq(NodeOutline.CHANNEL), anyString(), eq(viewer), any(Loc.class), any(Vec3.class),
        any(BlockStateRef.class), isNull(), anyBoolean(), eq(NodeOutline.DURATION_TICKS));
  }

  private NodeBundle nodeBundle(NodeKey key, UUID owner) {
    NodeIndex index = new NodeIndex();
    long generation = index.beginLoad(WORLD, key.chunkKey());
    index.completeLoad(WORLD, key.chunkKey(), generation, Map.of(key, NodeRecord.builder(owner, NodeType.GUARD, BlockFace.UP).build()));
    NodeBundle bundle = bundle();
    NodeRenderer renderer = mock(NodeRenderer.class);
    lenient().when(renderer.bundle(key)).thenReturn(Optional.of(bundle));
    NodeService nodes = mock(NodeService.class);
    lenient().when(nodes.index()).thenReturn(index);
    lenient().when(nodes.renderer()).thenReturn(renderer);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(nodes));
    lenient().when(registry.loadedOf(any(UUID.class))).thenReturn(List.of());
    return bundle;
  }

  private static NodeBundle bundle() {
    ItemDisplayRef icon = mock(ItemDisplayRef.class);
    EntityRef hitbox = mock(EntityRef.class);
    TextDisplayRef label = mock(TextDisplayRef.class);
    for (EntityRef entity : List.of(icon, hitbox, label)) {
      lenient().when(entity.isValid()).thenReturn(true);
    }
    return new NodeBundle(icon, hitbox, label);
  }

  private PlayerRef viewer(UUID id) {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(inventory.mainHand()).thenReturn(mock(ItemRef.class));
    lenient().when(inventory.offHand()).thenReturn(mock(ItemRef.class));
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.location()).thenAnswer(invocation -> new Loc(world, 0D, 64D, 0D));
    lenient().when(player.eyeLocation()).thenAnswer(invocation -> new Loc(world, 0D, 65.6D, 0D));
    lenient().when(player.world()).thenReturn(world);
    return player;
  }

  private boolean runIfAccepted(Runnable task) {
    if (!schedulerAccepts.get()) {
      return false;
    }
    task.run();
    return true;
  }

  private TextDisplayRef spawnDisplay() {
    TextDisplayRef display = mock(TextDisplayRef.class);
    AtomicBoolean valid = new AtomicBoolean(true);
    lenient().when(display.isValid()).thenAnswer(invocation -> valid.get());
    lenient().doAnswer(invocation -> {
      valid.set(false);
      return null;
    }).when(display).remove();
    lenient().when(display.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(display.transform()).thenReturn(Transform.IDENTITY);
    spawned.add(display);
    return display;
  }

  private GolemHandle golem(AtomicReference<Double> distance) {
    MobRef golem = mock(MobRef.class);
    UUID entityId = UUID.randomUUID();
    lenient().when(golem.uuid()).thenReturn(entityId);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.location()).thenAnswer(invocation -> new Loc(world, distance.get(), 64D, 0D));
    lenient().when(golem.addPassenger(any(EntityRef.class))).thenReturn(true);
    lenient().when(golem.health()).thenReturn(100D);
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.workStatus()).thenReturn(GolemWorkStatus.WAITING);
    lenient().when(handle.entityId()).thenReturn(entityId);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(handle.isRetired()).thenReturn(false);
    return handle;
  }
}
