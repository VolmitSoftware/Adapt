package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeathSaveCooldownTest {
  @Test
  void everySaveSurvivesRuntimeCleanupAndARecreatedPlayer() {
    for (DeathSaveCooldown cooldown : DeathSaveCooldown.values()) {
      Map<Id, Object> saved = new HashMap<>();
      UUID playerId = UUID.randomUUID();
      PlayerRef original = player(playerId, new MapData(saved));

      assertThat(cooldown.activate(original, 1_000L, 60_000L)).isTrue();
      PlayerStateRegistry.clearPlayer(playerId);
      PlayerRef reconnected = player(playerId, new MapData(saved));

      assertThat(cooldown.remaining(reconnected, 2_000L)).isEqualTo(59_000L);
      assertThat(cooldown.activate(reconnected, 2_000L, 1L)).isFalse();
      assertThat(cooldown.isReady(reconnected, 60_999L)).isFalse();
      assertThat(cooldown.isReady(reconnected, 61_000L)).isTrue();
    }
  }

  @Test
  void independentSaveKeysDoNotConsumeUnselectedSaves() {
    PlayerRef player = player(UUID.randomUUID(), new MapData(new HashMap<>()));

    assertThat(DeathSaveCooldown.LAST_RITES.activate(player, 1_000L, 60_000L)).isTrue();

    assertThat(DeathSaveCooldown.LAST_RITES.isReady(player, 1_001L)).isFalse();
    assertThat(DeathSaveCooldown.HOURGLASS_GUARD.isReady(player, 1_001L)).isTrue();
    assertThat(DeathSaveCooldown.VOID_SKIN.isReady(player, 1_001L)).isTrue();
  }

  @Test
  void expiryUsesTheCommittedDurationRatherThanTheNextRequestedDuration() {
    PlayerRef player = player(UUID.randomUUID(), new MapData(new HashMap<>()));

    assertThat(DeathSaveCooldown.VOID_SKIN.activate(player, 1_000L, 60_000L)).isTrue();
    assertThat(DeathSaveCooldown.VOID_SKIN.activate(player, 2_000L, 10L)).isFalse();
    assertThat(DeathSaveCooldown.VOID_SKIN.remaining(player, 2_000L)).isEqualTo(59_000L);
    assertThat(DeathSaveCooldown.VOID_SKIN.activate(player, 61_000L, 10L)).isTrue();
    assertThat(DeathSaveCooldown.VOID_SKIN.remaining(player, 61_000L)).isEqualTo(10L);
  }

  @Test
  void overflowCannotMakeACooldownImmediatelyReady() {
    PlayerRef player = player(UUID.randomUUID(), new MapData(new HashMap<>()));

    assertThat(DeathSaveCooldown.LAST_RITES.activate(player, 10L, Long.MAX_VALUE)).isTrue();
    assertThat(DeathSaveCooldown.LAST_RITES.remaining(player, 10L)).isEqualTo(Long.MAX_VALUE - 10L);
  }

  @Test
  void failedPersistenceRefusesActivationAndReportsTheException() {
    MapData data = new MapData(new HashMap<>());
    PlayerRef player = player(UUID.randomUUID(), data);
    IllegalStateException failure = new IllegalStateException("Storage unavailable");
    data.failure = failure;

    PlatformLog previous = AdaptEnvironment.log();
    Path previousData = AdaptEnvironment.dataDirectory();
    PlatformLog logging = mock(PlatformLog.class);
    AdaptEnvironment.install(logging, () -> previousData);
    try {
      assertThat(DeathSaveCooldown.HOURGLASS_GUARD.activate(player, 1_000L, 60_000L)).isFalse();
      verify(logging).error(anyString(), eq(failure));
    } finally {
      AdaptEnvironment.install(previous, () -> previousData);
    }
  }

  @Test
  void aDeadlineStoredUnderAnotherTypeKeepsTheSaveOnCooldownAndIsLeftAlone() {
    DataHolder data = mock(DataHolder.class);
    Id key = Id.adapt("death_save_tragoul_last_rites");
    when(data.has(any())).thenReturn(false);
    when(data.hasAny(key)).thenReturn(true);
    PlayerRef player = player(UUID.randomUUID(), data);

    PlatformLog previous = AdaptEnvironment.log();
    Path previousData = AdaptEnvironment.dataDirectory();
    AdaptEnvironment.install(mock(PlatformLog.class), () -> previousData);
    try {
      assertThat(DeathSaveCooldown.LAST_RITES.remaining(player, 1_000L)).isEqualTo(Long.MAX_VALUE);
      assertThat(DeathSaveCooldown.LAST_RITES.activate(player, 1_000L, 60_000L)).isFalse();
      verify(data, never()).set(any(), anyLong());
    } finally {
      AdaptEnvironment.install(previous, () -> previousData);
    }
  }

  private PlayerRef player(UUID id, DataHolder data) {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(id);
    when(player.data()).thenReturn(data);
    return player;
  }

  private static final class MapData implements DataHolder {
    private final Map<Id, Object> values;
    private RuntimeException failure;

    private MapData(Map<Id, Object> values) {
      this.values = values;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(DataKey<T> key) {
      return (T) values.get(key.id());
    }

    @Override
    public boolean has(DataKey<?> key) {
      return values.containsKey(key.id());
    }

    @Override
    public boolean hasAny(Id id) {
      return values.containsKey(id);
    }

    @Override
    public <T> void set(DataKey<T> key, T value) {
      if (failure != null) {
        throw failure;
      }
      values.put(key.id(), value);
    }

    @Override
    public void remove(Id id) {
      values.remove(id);
    }

    @Override
    public Set<Id> keys() {
      return values.keySet();
    }

    @Override
    public boolean isEmpty() {
      return values.isEmpty();
    }
  }
}
