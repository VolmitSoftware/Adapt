package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeAreaSelectorTest {
  @Test
  void dominantLookAxisIncludesUpAndDown() {
    assertThat(NodeAreaSelector.direction(new Vec3(0.1, 0.9, -0.2))).isEqualTo(NodeEdits.Axis.Y);
    assertThat(NodeAreaSelector.direction(new Vec3(0.1, -0.9, -0.2))).isEqualTo(NodeEdits.Axis.Y);
    assertThat(NodeAreaSelector.direction(new Vec3(-0.9, 0.1, -0.2))).isEqualTo(NodeEdits.Axis.X);
    assertThat(NodeAreaSelector.direction(new Vec3(0.1, 0.2, -0.9))).isEqualTo(NodeEdits.Axis.Z);
  }

  @Test
  void doubleSneakChoosesSmallestOwnedContainingAreaAndOrdinaryScrollingExits() {
    UUID owner = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(worldId);
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(owner);
    when(player.world()).thenReturn(world);
    when(player.location()).thenReturn(new Loc(world, 0.5D, 65.5D, 0.5D));
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef crook = mock(ItemRef.class);
    when(player.inventory()).thenReturn(inventory);
    when(inventory.mainHand()).thenReturn(crook);
    NodeKey small = new NodeKey(worldId, 0, 64, 0, BlockFace.UP);
    NodeKey large = new NodeKey(worldId, 1, 64, 0, BlockFace.UP);
    NodeIndex index = new NodeIndex();
    index.completeLoad(worldId, small.chunkKey(), index.beginLoad(worldId, small.chunkKey()), Map.of(
        small, NodeRecord.builder(owner, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build(),
        large, NodeRecord.builder(owner, NodeType.HARVEST, BlockFace.UP).build()));
    NodeService service = mock(NodeService.class);
    when(service.index()).thenReturn(index);
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.level(player)).thenReturn(3);
    try (MockedStatic<GolemItems> items = mockStatic(GolemItems.class);
         MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class);
         MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
         MockedStatic<GolemGuiSupport> feedback = mockStatic(GolemGuiSupport.class)) {
      items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
      runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
      scheduling.when(() -> Tasks.runEntity(any(PlayerRef.class), any(Runnable.class), anyInt())).thenReturn(true);
      NodeAreaSelector selector = new NodeAreaSelector();
      selector.on(new PlayerToggleSneakEvent(player, true));
      assertThat(selector.swallows(player)).isFalse();
      selector.on(new PlayerToggleSneakEvent(player, false));
      selector.on(new PlayerToggleSneakEvent(player, true));
      assertThat(selector.selecting(player, small)).isTrue();
      assertThat(selector.selecting(player, large)).isFalse();
      PlayerItemHeldEvent scroll = new PlayerItemHeldEvent(player, 0, 1);
      selector.on(scroll);
      assertThat(scroll.isCancelled()).isFalse();
      assertThat(selector.swallows(player)).isFalse();
    }
  }

  @Test
  void shiftScrollConsumesHotbarChangeAndExitingInvalidatesQueuedMove() {
    UUID owner = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(worldId);
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(owner);
    when(player.world()).thenReturn(world);
    when(player.isSneaking()).thenReturn(true);
    when(player.eyeLocation()).thenReturn(new Loc(world, 0D, 65D, 0D, 0F, -90F));
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef crook = mock(ItemRef.class);
    when(player.inventory()).thenReturn(inventory);
    when(inventory.mainHand()).thenReturn(crook);
    NodeKey key = new NodeKey(worldId, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(owner, NodeType.HARVEST, BlockFace.UP).build();
    NodeService service = mock(NodeService.class);
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.level(player)).thenReturn(3);
    when(policy.maxAreaRadius(3)).thenReturn(8);
    List<Runnable> pending = new ArrayList<>();
    try (MockedStatic<GolemItems> items = mockStatic(GolemItems.class);
         MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class);
         MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
         MockedStatic<AdaptCore> core = mockStatic(AdaptCore.class);
         MockedStatic<GolemGuiSupport> feedback = mockStatic(GolemGuiSupport.class)) {
      items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
      runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
      PlatformHost host = mock(PlatformHost.class);
      AdaptPlatform platform = mock(AdaptPlatform.class);
      when(platform.host()).thenReturn(host);
      when(host.world(worldId)).thenReturn(world);
      core.when(AdaptCore::platform).thenReturn(platform);
      scheduling.when(() -> Tasks.runEntity(any(PlayerRef.class), any(Runnable.class), anyInt())).thenReturn(true);
      scheduling.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(call -> {
        pending.add(call.getArgument(1));
        return true;
      });
      NodeAreaSelector selector = new NodeAreaSelector();
      selector.toggle(player, key, record);
      PlayerItemHeldEvent scroll = new PlayerItemHeldEvent(player, 8, 0);
      selector.on(scroll);
      assertThat(scroll.isCancelled()).isTrue();
      assertThat(pending).hasSize(1);
      selector.toggle(player, key, record);
      pending.getFirst().run();
      verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
    }
  }
}
