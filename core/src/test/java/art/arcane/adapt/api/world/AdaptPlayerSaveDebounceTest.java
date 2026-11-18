package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptPlayerSaveDebounceTest extends AdaptTestBase {
  @Test
  void thousandRequestsScheduleOnePlayerOwnedSave() {
    UUID playerId = UUID.randomUUID();
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(playerId);
    PlayerDataPersistenceQueue queue = mock(PlayerDataPersistenceQueue.class);
    when(core.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(core.getAdvancementManager()).thenReturn(mock(AdvancementManager.class));
    AdaptConfig config = localConfig();
    Deque<Runnable> tasks = new ArrayDeque<>();

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class);
         MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(20))).thenAnswer(invocation -> {
        tasks.addLast(invocation.getArgument(1, Runnable.class));
        return true;
      });
      AdaptPlayer adaptPlayer = new AdaptPlayer(player, new PlayerData());

      for (int index = 0; index < 1_000; index++) {
        adaptPlayer.requestSave();
      }

      assertThat(tasks).hasSize(1);
      tasks.removeFirst().run();
      assertThat(tasks).isEmpty();
      verify(queue).queueSave(eq(playerId), anyString(), any(File.class), anyLong());
    }
  }

  @Test
  void requestArrivingDuringSerializationSchedulesAnotherSave() {
    UUID playerId = UUID.randomUUID();
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(playerId);
    PlayerDataPersistenceQueue queue = mock(PlayerDataPersistenceQueue.class);
    when(core.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(core.getAdvancementManager()).thenReturn(mock(AdvancementManager.class));
    AdaptConfig config = localConfig();
    Deque<Runnable> tasks = new ArrayDeque<>();
    AtomicReference<AdaptPlayer> owner = new AtomicReference<>();
    AtomicBoolean requestDuringSave = new AtomicBoolean(true);
    PlayerData data = spy(new PlayerData());
    doAnswer(invocation -> {
      if (requestDuringSave.compareAndSet(true, false)) {
        owner.get().requestSave();
      }
      return "{}";
    }).when(data).toJson(false);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class);
         MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(20))).thenAnswer(invocation -> {
        tasks.addLast(invocation.getArgument(1, Runnable.class));
        return true;
      });
      AdaptPlayer adaptPlayer = new AdaptPlayer(player, data);
      owner.set(adaptPlayer);

      adaptPlayer.requestSave();
      tasks.removeFirst().run();

      assertThat(tasks).hasSize(1);
      tasks.removeFirst().run();
      assertThat(tasks).isEmpty();
      verify(queue, times(2)).queueSave(eq(playerId), anyString(), any(File.class), anyLong());
    }
  }

  private static AdaptConfig localConfig() {
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = mock(AdaptConfig.SqlSettings.class);
    when(config.getSql()).thenReturn(sqlSettings);
    when(sqlSettings.isEnabled()).thenReturn(false);
    return config;
  }
}
