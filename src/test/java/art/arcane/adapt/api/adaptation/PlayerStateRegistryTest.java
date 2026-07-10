package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

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
}
