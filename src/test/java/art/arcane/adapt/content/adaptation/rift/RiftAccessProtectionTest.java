package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RiftAccessProtectionTest {
  @Test
  void remoteOpenUsesAStandardBlockInteractionAndHonorsItsDenial() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack portkey = mock(ItemStack.class);
    Block block = mock(Block.class);
    PluginManager pluginManager = mock(PluginManager.class);
    AtomicReference<PlayerInteractEvent> dispatched = new AtomicReference<>();
    stubBlockActionContext(player, block);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(portkey);
    doAnswer(invocation -> {
      Event rawEvent = invocation.getArgument(0);
      assertThat(rawEvent).isExactlyInstanceOf(PlayerInteractEvent.class);
      PlayerInteractEvent event = (PlayerInteractEvent) rawEvent;
      assertThat(ProtectionEventProbe.isActive(event)).isTrue();
      dispatched.set(event);
      event.setUseInteractedBlock(Event.Result.DENY);
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptContainerOpen(player, List.of(block));
    }

    PlayerInteractEvent event = dispatched.get();
    assertThat(allowed).isFalse();
    assertThat(event.getPlayer()).isSameAs(player);
    assertThat(event.getAction()).isEqualTo(Action.RIGHT_CLICK_BLOCK);
    assertThat(event.getItem()).isSameAs(portkey);
    assertThat(event.getClickedBlock()).isSameAs(block);
    assertThat(event.getBlockFace()).isEqualTo(BlockFace.UP);
    assertThat(event.getHand()).isEqualTo(EquipmentSlot.HAND);
    assertThat(ProtectionEventProbe.isActive(event)).isFalse();
  }

  @Test
  void containerOpenHonorsStandardEventCancellation() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    Block block = mock(Block.class);
    PluginManager pluginManager = mock(PluginManager.class);
    stubBlockActionContext(player, block);
    when(player.getInventory()).thenReturn(inventory);
    doAnswer(invocation -> {
      PlayerInteractEvent event = invocation.getArgument(0);
      event.setCancelled(true);
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptContainerOpen(player, List.of(block));
    }

    assertThat(allowed).isFalse();
  }

  @Test
  void containerOpenAllowsDefaultAndExplicitlyAllowedBlockUse() {
    for (Event.Result result : List.of(Event.Result.DEFAULT, Event.Result.ALLOW)) {
      Player player = mock(Player.class);
      PlayerInventory inventory = mock(PlayerInventory.class);
      Block block = mock(Block.class);
      PluginManager pluginManager = mock(PluginManager.class);
      stubBlockActionContext(player, block);
      when(player.getInventory()).thenReturn(inventory);
      doAnswer(invocation -> {
        PlayerInteractEvent event = invocation.getArgument(0);
        event.setUseInteractedBlock(result);
        return null;
      }).when(pluginManager).callEvent(any(Event.class));

      boolean allowed;
      try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        allowed = ProtectionEventProbe.attemptContainerOpen(player, List.of(block));
      }

      assertThat(allowed).as(result.name()).isTrue();
    }
  }

  @Test
  void eitherDoubleChestHalfCanDenyContainerOpen() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    Block first = mock(Block.class);
    Block second = mock(Block.class);
    PluginManager pluginManager = mock(PluginManager.class);
    List<Block> dispatched = new ArrayList<>();
    stubBlockActionContext(player, first, second);
    when(player.getInventory()).thenReturn(inventory);
    doAnswer(invocation -> {
      PlayerInteractEvent event = invocation.getArgument(0);
      dispatched.add(event.getClickedBlock());
      if (event.getClickedBlock() == second) {
        event.setCancelled(true);
      }
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptContainerOpen(player, List.of(first, second));
    }

    assertThat(allowed).isFalse();
    assertThat(dispatched).containsExactly(first, second);
  }

  @Test
  void doubleChestAccessIncludesBothPhysicalBlocks() {
    Block boundBlock = mock(Block.class);
    Block otherBlock = mock(Block.class);
    Inventory inventory = mock(Inventory.class);
    DoubleChest doubleChest = mock(DoubleChest.class);
    Chest left = mock(Chest.class);
    Chest right = mock(Chest.class);
    when(inventory.getHolder()).thenReturn(doubleChest);
    when(doubleChest.getLeftSide()).thenReturn(left);
    when(doubleChest.getRightSide()).thenReturn(right);
    when(left.getBlock()).thenReturn(boundBlock);
    when(right.getBlock()).thenReturn(otherBlock);

    assertThat(ProtectionEventProbe.containerBlocks(boundBlock, inventory))
        .containsExactly(boundBlock, otherBlock);
  }

  @Test
  void doubleChestTargetsIndexBothBlocksAndCrossChunkAnchors() {
    UUID worldId = UUID.randomUUID();
    World world = mock(World.class);
    Block first = mock(Block.class);
    Block second = mock(Block.class);
    Location firstLocation = new Location(world, 15, 70, 8);
    Location secondLocation = new Location(world, 16, 70, 8);
    when(world.getUID()).thenReturn(worldId);
    when(first.getLocation()).thenReturn(firstLocation);
    when(second.getLocation()).thenReturn(secondLocation);

    RiftAccess.ContainerTargets targets = RiftAccess.containerTargets(List.of(first, second));

    RiftAccessViewRegistry.BlockKey firstBlock = new RiftAccessViewRegistry.BlockKey(worldId, 15, 70, 8);
    RiftAccessViewRegistry.BlockKey secondBlock = new RiftAccessViewRegistry.BlockKey(worldId, 16, 70, 8);
    RiftAccessViewRegistry.ChunkKey firstChunk = new RiftAccessViewRegistry.ChunkKey(worldId, 0, 0);
    RiftAccessViewRegistry.ChunkKey secondChunk = new RiftAccessViewRegistry.ChunkKey(worldId, 1, 0);
    assertThat(targets.blockKeys()).containsExactlyInAnyOrder(firstBlock, secondBlock);
    assertThat(targets.chunkAnchors()).containsOnlyKeys(firstChunk, secondChunk);
    assertThat(targets.chunkAnchors().get(firstChunk)).isEqualTo(firstLocation);
    assertThat(targets.chunkAnchors().get(secondChunk)).isEqualTo(secondLocation);
  }

  @Test
  void doubleChestTargetsDeduplicateASharedChunkTicket() {
    UUID worldId = UUID.randomUUID();
    World world = mock(World.class);
    Block first = mock(Block.class);
    Block second = mock(Block.class);
    when(world.getUID()).thenReturn(worldId);
    when(first.getLocation()).thenReturn(new Location(world, 4, 70, 8));
    when(second.getLocation()).thenReturn(new Location(world, 5, 70, 8));

    RiftAccess.ContainerTargets targets = RiftAccess.containerTargets(List.of(first, second));

    assertThat(targets.blockKeys()).hasSize(2);
    assertThat(targets.chunkAnchors())
        .containsOnlyKeys(new RiftAccessViewRegistry.ChunkKey(worldId, 0, 0));
  }

  @Test
  void doubleChestOwnershipChecksBothPhysicalHalves() {
    Block first = mock(Block.class);
    Block second = mock(Block.class);
    Location firstLocation = mock(Location.class);
    Location secondLocation = mock(Location.class);
    when(first.getLocation()).thenReturn(firstLocation);
    when(second.getLocation()).thenReturn(secondLocation);

    try (MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      scheduling.when(() -> FoliaScheduler.isOwnedByCurrentRegion(firstLocation)).thenReturn(true);
      scheduling.when(() -> FoliaScheduler.isOwnedByCurrentRegion(secondLocation)).thenReturn(false);

      assertThat(RiftAccess.areContainerRegionsOwned(List.of(first, second))).isFalse();

      scheduling.verify(() -> FoliaScheduler.isOwnedByCurrentRegion(firstLocation));
      scheduling.verify(() -> FoliaScheduler.isOwnedByCurrentRegion(secondLocation));
    }
  }

  private static void stubBlockActionContext(Player player, Block... blocks) {
    World world = mock(World.class);
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(world);
    for (Block block : blocks) {
      when(block.getWorld()).thenReturn(world);
    }
  }
}
