package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DeathSaveCooldownTest {
  @Test
  void everySaveSurvivesRuntimeCleanupAndARecreatedPlayer() {
    for (DeathSaveCooldown cooldown : DeathSaveCooldown.values()) {
      Map<NamespacedKey, Long> saved = new HashMap<>();
      UUID playerId = UUID.randomUUID();
      Player original = player(playerId, saved);

      assertThat(cooldown.activate(original, 1_000L, 60_000L)).isTrue();
      PlayerStateRegistry.clearPlayer(playerId);
      Player reconnected = player(playerId, saved);

      assertThat(cooldown.remaining(reconnected, 2_000L)).isEqualTo(59_000L);
      assertThat(cooldown.activate(reconnected, 2_000L, 1L)).isFalse();
      assertThat(cooldown.isReady(reconnected, 60_999L)).isFalse();
      assertThat(cooldown.isReady(reconnected, 61_000L)).isTrue();
    }
  }

  @Test
  void independentSaveKeysDoNotConsumeUnselectedSaves() {
    Player player = player(UUID.randomUUID(), new HashMap<>());

    assertThat(DeathSaveCooldown.LAST_RITES.activate(player, 1_000L, 60_000L)).isTrue();

    assertThat(DeathSaveCooldown.LAST_RITES.isReady(player, 1_001L)).isFalse();
    assertThat(DeathSaveCooldown.HOURGLASS_GUARD.isReady(player, 1_001L)).isTrue();
    assertThat(DeathSaveCooldown.VOID_SKIN.isReady(player, 1_001L)).isTrue();
  }

  @Test
  void expiryUsesTheCommittedDurationRatherThanTheNextRequestedDuration() {
    Player player = player(UUID.randomUUID(), new HashMap<>());

    assertThat(DeathSaveCooldown.VOID_SKIN.activate(player, 1_000L, 60_000L)).isTrue();
    assertThat(DeathSaveCooldown.VOID_SKIN.activate(player, 2_000L, 10L)).isFalse();
    assertThat(DeathSaveCooldown.VOID_SKIN.remaining(player, 2_000L)).isEqualTo(59_000L);
    assertThat(DeathSaveCooldown.VOID_SKIN.activate(player, 61_000L, 10L)).isTrue();
    assertThat(DeathSaveCooldown.VOID_SKIN.remaining(player, 61_000L)).isEqualTo(10L);
  }

  @Test
  void overflowCannotMakeACooldownImmediatelyReady() {
    Player player = player(UUID.randomUUID(), new HashMap<>());

    assertThat(DeathSaveCooldown.LAST_RITES.activate(player, 10L, Long.MAX_VALUE)).isTrue();
    assertThat(DeathSaveCooldown.LAST_RITES.remaining(player, 10L)).isEqualTo(Long.MAX_VALUE - 10L);
  }

  @Test
  void failedPersistenceRefusesActivationAndReportsTheException() {
    Player player = player(UUID.randomUUID(), new HashMap<>());
    IllegalStateException failure = new IllegalStateException("Storage unavailable");
    PersistentDataContainer data = player.getPersistentDataContainer();
    doThrow(failure).when(data).set(any(), eq(PersistentDataType.LONG), anyLong());

    try (MockedStatic<Adapt> logging = mockStatic(Adapt.class)) {
      assertThat(DeathSaveCooldown.HOURGLASS_GUARD.activate(player, 1_000L, 60_000L)).isFalse();
      logging.verify(() -> Adapt.error(failure));
    }
  }

  private Player player(UUID id, Map<NamespacedKey, Long> saved) {
    Player player = mock(Player.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(player.getUniqueId()).thenReturn(id);
    when(player.getPersistentDataContainer()).thenReturn(data);
    when(data.getOrDefault(any(), eq(PersistentDataType.LONG), anyLong()))
        .thenAnswer(call -> saved.getOrDefault(call.getArgument(0), call.getArgument(2)));
    doAnswer(call -> {
      saved.put(call.getArgument(0), call.getArgument(2));
      return null;
    }).when(data).set(any(), eq(PersistentDataType.LONG), anyLong());
    return player;
  }
}
