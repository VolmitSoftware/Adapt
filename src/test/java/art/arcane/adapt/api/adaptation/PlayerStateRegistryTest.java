package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStateRegistryTest extends AdaptTestBase {
  @AfterEach
  void resetRegistry() {
    PlayerStateRegistry.reset();
  }

  @Test
  void clearingPlayerRemovesStateFromEveryLiveMap() {
    UUID playerId = UUID.randomUUID();
    Map<UUID, String> first = PlayerStateRegistry.newPlayerMap();
    Map<UUID, Integer> second = PlayerStateRegistry.newPlayerMap();
    first.put(playerId, "value");
    second.put(playerId, 7);

    PlayerStateRegistry.clearPlayer(playerId);

    assertThat(first).doesNotContainKey(playerId);
    assertThat(second).doesNotContainKey(playerId);
  }

  @Test
  void resetUnregistersTheSharedQuitListener() {
    PlayerStateRegistry.newPlayerMap();

    PlayerStateRegistry.reset();

    verify(plugin).unregisterListener(any(PlayerStateRegistry.QuitListener.class));
  }

  @Test
  void resetClearsMapsThatRemainLiveAcrossReload() {
    Map<UUID, String> state = PlayerStateRegistry.newPlayerMap();
    state.put(UUID.randomUUID(), "value");

    PlayerStateRegistry.reset();

    assertThat(state).isEmpty();
  }

  @Test
  void sharedCleanupRunsAfterAdaptationQuitHandlers() throws NoSuchMethodException {
    EventHandler handler = PlayerStateRegistry.QuitListener.class
        .getMethod("on", PlayerQuitEvent.class)
        .getAnnotation(EventHandler.class);

    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
  }

  @Test
  void delayedQuitCleanupUsesThreadSafeRuntimeMembership() {
    UUID playerId = UUID.randomUUID();
    Map<UUID, String> state = PlayerStateRegistry.newPlayerMap();
    state.put(playerId, "value");
    Player player = mock(Player.class);
    PlayerQuitEvent event = mock(PlayerQuitEvent.class);
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer current = mock(AdaptPlayer.class);
    List<Runnable> scheduled = new ArrayList<>();
    when(player.getUniqueId()).thenReturn(playerId);
    when(event.getPlayer()).thenReturn(player);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(current);
    when(current.isRuntimeReady()).thenReturn(true);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.s(any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        scheduled.add(invocation.getArgument(0));
        return null;
      });

      new PlayerStateRegistry.QuitListener().on(event);
      scheduled.getFirst().run();
    }

    assertThat(state).containsKey(playerId);

    when(current.isRuntimeReady()).thenReturn(false);
    scheduled.clear();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.s(any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        scheduled.add(invocation.getArgument(0));
        return null;
      });

      new PlayerStateRegistry.QuitListener().on(event);
      scheduled.getFirst().run();
    }

    assertThat(state).doesNotContainKey(playerId);
    verify(player, never()).isOnline();
  }

  @Test
  void shutdownClearsSharedStateAfterAdaptationsUnregister() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/Adapt.java"));
    int stopSim = source.indexOf("public void stopSim()");
    int unregisterAdaptations = source.indexOf("adaptServer.unregister()", stopSim);
    int clearSharedState = source.indexOf("PlayerStateRegistry::reset", stopSim);

    assertThat(unregisterAdaptations).isGreaterThan(stopSim);
    assertThat(clearSharedState).isGreaterThan(unregisterAdaptations);
  }
}
