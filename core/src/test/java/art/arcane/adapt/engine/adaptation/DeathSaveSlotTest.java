package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.content.DeathSaveSlot;
import art.arcane.adapt.content.adaptation.chronos.ChronosHourglassGuard;
import art.arcane.adapt.content.adaptation.rift.RiftVoidEscape;
import art.arcane.adapt.content.adaptation.tragoul.TragoulLastRites;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.MockedEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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

class DeathSaveSlotTest {
  private MockedEngine engine;

  @BeforeEach
  void startEngine() {
    engine = new MockedEngine(TestCores.install());
  }

  @AfterEach
  void stopEngine() {
    engine.close();
    TestCores.clear();
  }

  private static final DeathSaveSlot ADDON_SLOT = DeathSaveSlot.of(Id.of("alchemist", "philosophers_stone"));

  @Test
  void everySaveSurvivesRuntimeCleanupAndARecreatedPlayer() {
    for (DeathSaveSlot cooldown : List.of(TragoulLastRites.DEATH_SAVE, ChronosHourglassGuard.DEATH_SAVE,
        RiftVoidEscape.DEATH_SAVE, ADDON_SLOT)) {
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
  void equalIdsAreOneSlotAndSlashesBecomeUnderscoresInTheKey() {
    DeathSaveSlot nested = DeathSaveSlot.of(Id.of("alchemist", "alchemy/elixir"));
    assertThat(DeathSaveSlot.of(Id.of("alchemist", "alchemy/elixir"))).isEqualTo(nested).hasSameHashCodeAs(nested);
    assertThat(nested.deadlineKey().id()).isEqualTo(Id.of("alchemist", "death_save_alchemy_elixir"));
    assertThat(TragoulLastRites.DEATH_SAVE.deadlineKey().id()).isEqualTo(Id.adapt("death_save_tragoul_last_rites"));

    PlayerRef player = player(UUID.randomUUID(), new MapData(new HashMap<>()));
    assertThat(nested.activate(player, 1_000L, 60_000L)).isTrue();
    assertThat(DeathSaveSlot.of(Id.of("alchemist", "alchemy/elixir")).isReady(player, 1_001L)).isFalse();
  }

  @Test
  void independentSaveKeysDoNotConsumeUnselectedSaves() {
    PlayerRef player = player(UUID.randomUUID(), new MapData(new HashMap<>()));

    assertThat(TragoulLastRites.DEATH_SAVE.activate(player, 1_000L, 60_000L)).isTrue();

    assertThat(TragoulLastRites.DEATH_SAVE.isReady(player, 1_001L)).isFalse();
    assertThat(ChronosHourglassGuard.DEATH_SAVE.isReady(player, 1_001L)).isTrue();
    assertThat(RiftVoidEscape.DEATH_SAVE.isReady(player, 1_001L)).isTrue();
    assertThat(ADDON_SLOT.isReady(player, 1_001L)).isTrue();
  }

  @Test
  void expiryUsesTheCommittedDurationRatherThanTheNextRequestedDuration() {
    PlayerRef player = player(UUID.randomUUID(), new MapData(new HashMap<>()));

    assertThat(RiftVoidEscape.DEATH_SAVE.activate(player, 1_000L, 60_000L)).isTrue();
    assertThat(RiftVoidEscape.DEATH_SAVE.activate(player, 2_000L, 10L)).isFalse();
    assertThat(RiftVoidEscape.DEATH_SAVE.remaining(player, 2_000L)).isEqualTo(59_000L);
    assertThat(RiftVoidEscape.DEATH_SAVE.activate(player, 61_000L, 10L)).isTrue();
    assertThat(RiftVoidEscape.DEATH_SAVE.remaining(player, 61_000L)).isEqualTo(10L);
  }

  @Test
  void overflowCannotMakeACooldownImmediatelyReady() {
    PlayerRef player = player(UUID.randomUUID(), new MapData(new HashMap<>()));

    assertThat(TragoulLastRites.DEATH_SAVE.activate(player, 10L, Long.MAX_VALUE)).isTrue();
    assertThat(TragoulLastRites.DEATH_SAVE.remaining(player, 10L)).isEqualTo(Long.MAX_VALUE - 10L);
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
      assertThat(ChronosHourglassGuard.DEATH_SAVE.activate(player, 1_000L, 60_000L)).isFalse();
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
      assertThat(TragoulLastRites.DEATH_SAVE.remaining(player, 1_000L)).isEqualTo(Long.MAX_VALUE);
      assertThat(TragoulLastRites.DEATH_SAVE.activate(player, 1_000L, 60_000L)).isFalse();
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
