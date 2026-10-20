package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
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
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoards;
import art.arcane.adapt.api.golem.GolemWorkSettings;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeStatus;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuardNodeBehaviorTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
  private static final List<GolemCoreType> GUARDIAN = List.of(GolemCoreType.GUARDIAN);

  private final Adaptation<?> policy = mock(Adaptation.class);
  private final GuardNodeBehavior behavior = new GuardNodeBehavior(policy);
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;

  @BeforeEach
  void mockRuntime() {
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
  }

  @AfterEach
  void close() {
    scheduling.close();
    runtime.close();
  }

  @Test
  void itActsForTheGuardianCoreAdaptation() {
    assertThat(behavior.policy()).isSameAs(policy);
  }

  @Test
  void guardNodesGuideTheirOwnersGuardianGolemsOnTheNodeChannel() {
    NodeRecord red = guard(OWNER).channel(DyeColor.RED).build();
    NodeRecord open = guard(OWNER).build();

    assertThat(GuardNodeBehavior.guards(red, OWNER, ChannelMask.of(DyeColor.RED), GUARDIAN)).isTrue();
    assertThat(GuardNodeBehavior.guards(red, OWNER, ChannelMask.of(DyeColor.BLUE), GUARDIAN)).isFalse();
    assertThat(GuardNodeBehavior.guards(red, OWNER, ChannelMask.NONE, GUARDIAN)).isFalse();
    assertThat(GuardNodeBehavior.guards(open, OWNER, ChannelMask.NONE, GUARDIAN)).isTrue();
    assertThat(GuardNodeBehavior.guards(open, OWNER, ChannelMask.of(DyeColor.BLUE), GUARDIAN)).isTrue();
    assertThat(GuardNodeBehavior.guards(open, STRANGER, ChannelMask.NONE, GUARDIAN)).isFalse();
    assertThat(GuardNodeBehavior.guards(open, OWNER, ChannelMask.NONE, List.of(GolemCoreType.GATHERER))).isFalse();
    assertThat(GuardNodeBehavior.guards(NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).build(), OWNER, ChannelMask.NONE, GUARDIAN)).isFalse();
  }

  @Test
  void scanningPostsNoJobsAndActingFails() {
    NodeContext context = mock(NodeContext.class);
    ScanBudget budget = mock(ScanBudget.class);

    behavior.scan(context, budget);

    verifyNoInteractions(context);
    assertThat(behavior.act(mock(GolemContext.class), mock(GolemTask.class))).isEqualTo(GolemActResult.FAILED);
  }

  @Test
  void engagingANodeShowsItWorkingForTheActivityWindow() {
    GolemTaskBoards boards = new GolemTaskBoards(key -> Optional.of(guard(OWNER).build()));
    runtime.when(GolemancyRuntime::boards).thenReturn(Optional.of(boards));
    NodeKey key = new NodeKey(WORLD, 4, 64, 4, BlockFace.UP);

    behavior.engaged(key, 1_000L);

    assertThat(boards.board(WORLD).status(key, 1_000L)).isEqualTo(NodeStatus.WORKING);
    assertThat(boards.board(WORLD).status(key, 11_000L)).isEqualTo(NodeStatus.IDLE);
  }

  @Test
  void aGolemGuardsItsOwnersActiveMatchingGuardNodesWithinWorkRadiusOfHome() {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(WORLD);
    NodeKey near = new NodeKey(WORLD, 6, 64, 0, BlockFace.UP);
    NodeKey offChannel = new NodeKey(WORLD, 0, 64, 6, BlockFace.UP);
    NodeKey stranger = new NodeKey(WORLD, -6, 64, 0, BlockFace.UP);
    NodeKey far = new NodeKey(WORLD, 40, 64, 0, BlockFace.UP);
    NodeKey unpowered = new NodeKey(WORLD, 0, 64, -6, BlockFace.UP);
    NodeKey deposit = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);
    NodeKey unloaded = new NodeKey(WORLD, 0, 64, -20, BlockFace.UP);
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(near, guard(OWNER).build());
    nodes.put(offChannel, guard(OWNER).channel(DyeColor.BLUE).build());
    nodes.put(stranger, guard(STRANGER).build());
    nodes.put(far, guard(OWNER).build());
    nodes.put(unpowered, guard(OWNER).redstone(RedstoneMode.POWERED).build());
    nodes.put(deposit, NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP).build());
    nodes.put(unloaded, guard(OWNER).redstone(RedstoneMode.POWERED).build());
    NodeService service = service(nodes);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::workSettings).thenReturn(GolemWorkSettings.defaults());
    Block unpoweredBlock = mock(Block.class);
    when(unpoweredBlock.getWorld()).thenReturn(world);
    when(unpoweredBlock.getZ()).thenReturn(-6);
    when(world.getBlockAt(0, 64, -6)).thenReturn(unpoweredBlock);
    when(world.isChunkLoaded(0, -1)).thenReturn(true);
    when(world.isChunkLoaded(-1, -1)).thenReturn(true);

    List<NodeIndex.Entry> guarded = behavior.nodesFor(golem(ChannelMask.NONE), world, new Location(world, 0.5D, 64D, 0.5D), 1_000L);

    assertThat(guarded).extracting(NodeIndex.Entry::key).containsExactlyInAnyOrder(near, unloaded);
    verify(world, never()).getBlockAt(0, 64, -20);
  }

  @Test
  void aGolemsGuardNodesAreCachedBrieflyAndRefreshedWhenItsChannelChanges() {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(WORLD);
    NodeKey open = new NodeKey(WORLD, 6, 64, 0, BlockFace.UP);
    NodeKey red = new NodeKey(WORLD, 0, 64, 6, BlockFace.UP);
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(open, guard(OWNER).build());
    nodes.put(red, guard(OWNER).channel(DyeColor.RED).build());
    NodeService service = service(nodes);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::workSettings).thenReturn(GolemWorkSettings.defaults());
    GolemHandle golem = golem(ChannelMask.NONE);
    Location home = new Location(world, 0.5D, 64D, 0.5D);

    behavior.nodesFor(golem, world, home, 1_000L);
    behavior.nodesFor(golem, world, home, 1_000L + GuardNodeBehavior.NODE_CACHE_MILLIS - 1L);
    verify(service, times(1)).index();
    assertThat(behavior.pending()).isTrue();

    when(golem.state().channels()).thenReturn(ChannelMask.of(DyeColor.RED));
    assertThat(behavior.nodesFor(golem, world, home, 1_100L)).extracting(NodeIndex.Entry::key).containsExactlyInAnyOrder(open, red);
    verify(service, times(2)).index();

    behavior.nodesFor(golem, world, home, 1_100L + GuardNodeBehavior.NODE_CACHE_MILLIS);
    verify(service, times(3)).index();

    behavior.sweep(1_100L + GuardNodeBehavior.NODE_CACHE_MILLIS * 2L);
    assertThat(behavior.pending()).isFalse();
  }

  @Test
  void aTideRuneWidensTheGuardNodeSearchAroundHome() {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(WORLD);
    NodeKey near = new NodeKey(WORLD, 28, 64, 0, BlockFace.UP);
    NodeKey tideOnly = new NodeKey(WORLD, 36, 64, 0, BlockFace.UP);
    NodeKey beyond = new NodeKey(WORLD, 44, 64, 0, BlockFace.UP);
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(near, guard(OWNER).build());
    nodes.put(tideOnly, guard(OWNER).build());
    nodes.put(beyond, guard(OWNER).build());
    NodeService service = service(nodes);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::workSettings).thenReturn(GolemWorkSettings.defaults());
    Location home = new Location(world, 0.5D, 64D, 0.5D);

    assertThat(behavior.nodesFor(golem(ChannelMask.NONE), world, home, 1_000L)).extracting(NodeIndex.Entry::key).containsExactly(near);
    GolemRunes.Augments tide = runes(new GolemRuneEffects(8D, 0, 1D, 0D));
    try {
      assertThat(behavior.nodesFor(golem(ChannelMask.NONE), world, home, 1_000L)).extracting(NodeIndex.Entry::key)
          .containsExactlyInAnyOrder(near, tideOnly);
    } finally {
      GolemRunes.unregister(tide);
    }
  }

  @Test
  void aGuardianGuardsOnlyTheChannelsItsRunesLetItUse() {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(WORLD);
    NodeKey white = new NodeKey(WORLD, 6, 64, 0, BlockFace.UP);
    NodeKey red = new NodeKey(WORLD, 0, 64, 6, BlockFace.UP);
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(white, guard(OWNER).channel(DyeColor.WHITE).build());
    nodes.put(red, guard(OWNER).channel(DyeColor.RED).build());
    NodeService service = service(nodes);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::workSettings).thenReturn(GolemWorkSettings.defaults());
    Location home = new Location(world, 0.5D, 64D, 0.5D);
    int both = ChannelMask.of(DyeColor.WHITE, DyeColor.RED);

    assertThat(behavior.nodesFor(golem(both), world, home, 1_000L)).extracting(NodeIndex.Entry::key).containsExactly(white);
    GolemRunes.Augments order = runes(new GolemRuneEffects(0D, 1, 1D, 0D));
    try {
      assertThat(behavior.nodesFor(golem(both), world, home, 1_000L)).extracting(NodeIndex.Entry::key).containsExactlyInAnyOrder(white, red);
    } finally {
      GolemRunes.unregister(order);
    }
  }

  private static GolemRunes.Augments runes(GolemRuneEffects effects) {
    GolemRunes.Augments fixed = new GolemRunes.Augments() {
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
    return fixed;
  }

  private static NodeService service(Map<NodeKey, NodeRecord> nodes) {
    NodeIndex index = new NodeIndex();
    Map<Long, Map<NodeKey, NodeRecord>> chunks = new HashMap<>();
    for (Map.Entry<NodeKey, NodeRecord> node : nodes.entrySet()) {
      chunks.computeIfAbsent(node.getKey().chunkKey(), ignored -> new HashMap<>()).put(node.getKey(), node.getValue());
    }
    for (Map.Entry<Long, Map<NodeKey, NodeRecord>> chunk : chunks.entrySet()) {
      long generation = index.beginLoad(WORLD, chunk.getKey());
      index.completeLoad(WORLD, chunk.getKey(), generation, chunk.getValue());
    }
    NodeService service = mock(NodeService.class);
    when(service.index()).thenReturn(index);
    return service;
  }

  private static GolemHandle golem(int channels) {
    GolemState state = mock(GolemState.class);
    lenient().when(state.channels()).thenReturn(channels);
    lenient().when(state.cores()).thenReturn(GUARDIAN);
    lenient().when(state.home()).thenReturn(GolemState.Anchor.of(WORLD, 0, 64, 0));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    return handle;
  }

  private static NodeRecord.Builder guard(UUID owner) {
    return NodeRecord.builder(owner, NodeType.GUARD, BlockFace.UP);
  }
}
