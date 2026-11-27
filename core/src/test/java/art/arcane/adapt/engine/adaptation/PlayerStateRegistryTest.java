package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.engine.ComponentRegistrar;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStateRegistryTest {
  private AdaptCore core;
  private ComponentRegistrar registrar;

  @BeforeEach
  void installCore() {
    core = TestCores.install();
    registrar = mock(ComponentRegistrar.class);
    TestCores.setField(core, "registrar", registrar);
    PlayerStateRegistry.reset();
  }

  @AfterEach
  void resetRegistry() {
    PlayerStateRegistry.reset();
    TestCores.clear();
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

    verify(registrar).unregister(any(PlayerStateRegistry.QuitListener.class));
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
    Handle handler = PlayerStateRegistry.QuitListener.class
        .getMethod("on", PlayerQuitEvent.class)
        .getAnnotation(Handle.class);

    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
  }

  @Test
  void delayedQuitCleanupUsesThreadSafeRuntimeMembership() {
    UUID playerId = UUID.randomUUID();
    Map<UUID, String> state = PlayerStateRegistry.newPlayerMap();
    state.put(playerId, "value");
    PlayerRef player = mock(PlayerRef.class);
    PlayerQuitEvent event = new PlayerQuitEvent(player);
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer current = mock(AdaptPlayer.class);
    List<Runnable> scheduled = new ArrayList<>();
    when(player.uuid()).thenReturn(playerId);
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(current);
    when(current.isRuntimeReady()).thenReturn(true);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.s(any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        scheduled.add(invocation.getArgument(0));
        return null;
      });

      new PlayerStateRegistry.QuitListener().on(event);
      scheduled.getFirst().run();
    }

    assertThat(state).containsKey(playerId);

    when(current.isRuntimeReady()).thenReturn(false);
    scheduled.clear();
    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.s(any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        scheduled.add(invocation.getArgument(0));
        return null;
      });

      new PlayerStateRegistry.QuitListener().on(event);
      scheduled.getFirst().run();
    }

    assertThat(state).doesNotContainKey(playerId);
    verify(player, never()).isOnline();
  }
}
