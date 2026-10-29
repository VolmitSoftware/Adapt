package art.arcane.adapt.claims;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.claims.ClaimAction;
import art.arcane.adapt.api.claims.ClaimAttempt;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimRepository;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ClaimRaidFinalizationTest extends AdaptTestBase {
  private final UUID founder = UUID.randomUUID();
  private final UUID attacker = UUID.randomUUID();
  private final UUID worldId = UUID.randomUUID();
  private final ClaimBlock position = new ClaimBlock(worldId, 1609, 64, 1608);
  private final List<Runnable> delayed = new ArrayList<>();
  private ClaimManager manager;
  private ClaimProtectionListener listener;
  private PluginManager plugins;
  private Player player;
  private Block target;

  @BeforeEach
  void prepare() throws IOException {
    manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(dataFolder.toPath().resolve("claims.json")));
    manager.found(founder, "Meadow", new ClaimBlock(worldId, 1608, 64, 1608),
        new ClaimRanks(true, 1, 0, 0, false), new ClaimWorldAccess("world", true));
    manager.setOnline(founder, true, System.currentTimeMillis());
    ClaimsService claims = mock(ClaimsService.class);
    when(claims.enabled()).thenReturn(true);
    when(claims.manager()).thenReturn(manager);
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(attacker);
    when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    World world = mock(World.class);
    when(world.getUID()).thenReturn(worldId);
    target = mock(Block.class);
    when(target.getWorld()).thenReturn(world);
    when(target.getX()).thenReturn(position.x());
    when(target.getY()).thenReturn(position.y());
    when(target.getZ()).thenReturn(position.z());
    when(target.getLocation()).thenReturn(new Location(world, position.x(), position.y(), position.z()));
    changeTarget("minecraft:stone");
    listener = new ClaimProtectionListener(claims);
    plugins = mock(PluginManager.class);
    doAnswer(invocation -> {
      Event probe = invocation.getArgument(0);
      if (probe instanceof BlockBreakEvent broken) {
        listener.onBreak(broken);
      } else if (probe instanceof BlockPlaceEvent placed) {
        listener.onPlace(placed);
      }
      return null;
    }).when(plugins).callEvent(any(Event.class));
  }

  @AfterEach
  void close() throws IOException {
    manager.close();
  }

  @Test
  void lateBreakDenialRetainsOnlyPreviouslyCompletedAttempts() {
    seed(ClaimAction.BREAK, "minecraft:stone", 15);
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<J> scheduler = mockStatic(J.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
      captureScheduled(scheduler);
      BlockBreakEvent denied = new BlockBreakEvent(target, player);
      listener.onBreak(denied);
      assertThat(denied.isCancelled()).isFalse();
      denied.setCancelled(true);
      runScheduled();
      assertThat(manager.progress(position).orElseThrow().breaking()).isEqualTo(15 / 16D);

      BlockBreakEvent completed = new BlockBreakEvent(target, player);
      listener.onBreak(completed);
      assertThat(completed.isCancelled()).isFalse();
      assertThat(manager.progress(position).orElseThrow().breaking()).isEqualTo(15 / 16D);
      changeTarget("minecraft:air");
      runScheduled();
      assertThat(manager.progress(position)).isEmpty();
    }
  }

  @Test
  void latePlacementDenialRetainsOnlyPreviouslyCompletedAttempts() {
    seed(ClaimAction.PLACE, "minecraft:air", 63);
    BlockState replaced = mock(BlockState.class);
    BlockData air = data("minecraft:air");
    Location location = target.getLocation();
    when(replaced.getBlock()).thenReturn(target);
    when(replaced.getLocation()).thenReturn(location);
    when(replaced.getBlockData()).thenReturn(air);
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<J> scheduler = mockStatic(J.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
      captureScheduled(scheduler);
      BlockPlaceEvent denied = placement(replaced);
      listener.onPlace(denied);
      assertThat(denied.isCancelled()).isFalse();
      denied.setCancelled(true);
      changeTarget("minecraft:air");
      runScheduled();
      assertThat(manager.progress(position).orElseThrow().placement()).isEqualTo(63 / 64D);

      changeTarget("minecraft:stone");
      BlockPlaceEvent completed = placement(replaced);
      listener.onPlace(completed);
      assertThat(completed.isCancelled()).isFalse();
      assertThat(manager.progress(position).orElseThrow().placement()).isEqualTo(63 / 64D);
      runScheduled();
      assertThat(manager.progress(position)).isEmpty();
    }
  }

  private void seed(ClaimAction action, String state, int count) {
    long started = System.currentTimeMillis() - (count + 1L) * 1000;
    for (int index = 0; index < count; index++) {
      manager.attempt(new ClaimAttempt(attacker, position, action, state, started + index * 1000L, true));
    }
  }

  private BlockPlaceEvent placement(BlockState replaced) {
    return new BlockPlaceEvent(target, replaced, target, mock(ItemStack.class), player, true, EquipmentSlot.HAND);
  }

  private void captureScheduled(MockedStatic<J> scheduler) {
    scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
      delayed.add(invocation.getArgument(1));
      return true;
    });
  }

  private void runScheduled() {
    assertThat(delayed).hasSize(1);
    delayed.removeFirst().run();
  }

  private void changeTarget(String state) {
    BlockData replacement = data(state);
    when(target.getBlockData()).thenReturn(replacement);
  }

  private BlockData data(String state) {
    BlockData data = mock(BlockData.class);
    when(data.getAsString()).thenReturn(state);
    return data;
  }
}
