package art.arcane.adapt.util.common.plugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ProtectionEventProbeBlockUseTest {
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
      assertThat(rawEvent).isInstanceOf(PlayerInteractEvent.class);
      PlayerInteractEvent event = (PlayerInteractEvent) rawEvent;
      assertThat(ProtectionEventProbe.isActive(event)).isTrue();
      dispatched.set(event);
      event.setUseInteractedBlock(Event.Result.DENY);
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptBlockUse(player, block);
    }

    PlayerInteractEvent event = dispatched.get();
    assertThat(allowed).isFalse();
    assertThat(event.getPlayer()).isSameAs(player);
    assertThat(event.getAction()).isEqualTo(Action.RIGHT_CLICK_BLOCK);
    assertThat(event.getItem()).isSameAs(portkey);
    assertThat(event.getClickedBlock()).isSameAs(block);
    assertThat(event.getBlockFace()).isEqualTo(BlockFace.UP);
    assertThat(event.getHand()).isEqualTo(EquipmentSlot.HAND);
    assertThat(ProtectionEventProbe.isActive(event)).isTrue();
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
      allowed = ProtectionEventProbe.attemptBlockUse(player, block);
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
        allowed = ProtectionEventProbe.attemptBlockUse(player, block);
      }

      assertThat(allowed).as(result.name()).isTrue();
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
