package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FxViewersTest {
  private AdaptCore core;

  @BeforeEach
  void installCore() {
    core = TestCores.install();
  }

  @AfterEach
  void clearCore() {
    FxViewers.reset();
    TestCores.clear();
  }

  @Test
  void snapshotsUsePlayerOwnedPositionCaches() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerData data = new PlayerData();
    PlayerRef player = player();
    WorldRef world = world();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(data);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 12D, 64D, -8D));

    FxViewers.reset();
    FxViewers.bumpTick();
    FxViewers.Snapshot snapshot = FxViewers.current();

    assertThat(snapshot.countViewers(world, 12D, 64D, -8D, 1D)).isEqualTo(1);
    verify(player, never()).location();
  }

  @Test
  void perViewerEmissionBudgetsResetWithoutRebuildingPositions() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerData data = new PlayerData();
    PlayerRef player = player();
    WorldRef world = world();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(data);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0D, 64D, 0D));

    FxViewers.reset();
    FxViewers.bumpTick();
    FxViewers.Snapshot first = FxViewers.current();
    for (int i = 0; i < FxBudget.PER_VIEWER_EMISSION_CAP; i++) {
      assertThat(first.tryEmit(0)).isTrue();
    }
    assertThat(first.tryEmit(0)).isFalse();

    FxViewers.bumpTick();
    FxViewers.Snapshot second = FxViewers.current();

    assertThat(second).isSameAs(first);
    assertThat(second.tryEmit(0)).isTrue();
  }

  @Test
  void knownOptedOutPlayersDoNotUseUncachedDispatchFallback() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerData data = new PlayerData();
    data.setEffectsEnabled(false);
    PlayerRef player = player();
    WorldRef world = world();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayerSnapshot()).thenReturn(List.of(adaptPlayer));
    when(adaptPlayer.getData()).thenReturn(data);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getFxPosition()).thenReturn(new AdaptPlayer.FxPosition(world, 0D, 64D, 0D));
    FxViewers.reset();
    FxViewers.bumpTick();
    FxViewers.Snapshot snapshot = FxViewers.current();
    AtomicInteger emitted = new AtomicInteger();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      FxViewers.dispatch(List.of(player), viewer -> emitted.incrementAndGet());

      assertThat(snapshot.isKnown(player)).isTrue();
      assertThat(snapshot.countViewers(world, 0D, 64D, 0D, 1D)).isZero();
      assertThat(emitted.get()).isZero();
      scheduling.verifyNoInteractions();
    }
  }

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    return player;
  }

  private static WorldRef world() {
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(UUID.randomUUID());
    return world;
  }
}
