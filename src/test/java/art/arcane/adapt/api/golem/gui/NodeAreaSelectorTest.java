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
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
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
    assertThat(NodeAreaSelector.direction(new Vector(0.1, 0.9, -0.2))).isEqualTo(NodeEdits.Axis.Y);
    assertThat(NodeAreaSelector.direction(new Vector(0.1, -0.9, -0.2))).isEqualTo(NodeEdits.Axis.Y);
    assertThat(NodeAreaSelector.direction(new Vector(-0.9, 0.1, -0.2))).isEqualTo(NodeEdits.Axis.X);
    assertThat(NodeAreaSelector.direction(new Vector(0.1, 0.2, -0.9))).isEqualTo(NodeEdits.Axis.Z);
  }

  @Test
  void doubleSneakChoosesSmallestOwnedContainingAreaAndOrdinaryScrollingExits() {
    UUID owner = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    World world = mock(World.class);
    when(world.getUID()).thenReturn(worldId);
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(owner);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(new Location(world, 0.5D, 65.5D, 0.5D));
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack crook = mock(ItemStack.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(crook);
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
         MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<GolemGuiSupport> feedback = mockStatic(GolemGuiSupport.class)) {
      items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
      runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
      scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class), anyInt())).thenReturn(true);
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
    World world = mock(World.class);
    when(world.getUID()).thenReturn(worldId);
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(owner);
    when(player.getWorld()).thenReturn(world);
    when(player.isSneaking()).thenReturn(true);
    when(player.getEyeLocation()).thenReturn(new Location(world, 0D, 65D, 0D, 0F, -90F));
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack crook = mock(ItemStack.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(crook);
    NodeKey key = new NodeKey(worldId, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(owner, NodeType.HARVEST, BlockFace.UP).build();
    NodeService service = mock(NodeService.class);
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    when(policy.level(player)).thenReturn(3);
    when(policy.maxAreaRadius(3)).thenReturn(8);
    List<Runnable> pending = new ArrayList<>();
    try (MockedStatic<GolemItems> items = mockStatic(GolemItems.class);
         MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class);
         MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<GolemGuiSupport> feedback = mockStatic(GolemGuiSupport.class)) {
      items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
      runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
      runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class), anyInt())).thenReturn(true);
      scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
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
