package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  @Test
  void sharedCleanupRunsAfterAdaptationQuitHandlers() throws NoSuchMethodException {
    EventHandler handler = PlayerStateRegistry.QuitListener.class
        .getMethod("on", PlayerQuitEvent.class)
        .getAnnotation(EventHandler.class);

    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
  }

  @Test
  void shutdownClearsSharedStateAfterAdaptationsUnregister() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/Adapt.java"));
    int stopSim = source.indexOf("public void stopSim()");
    int unregisterAdaptations = source.indexOf("adaptServer.unregister();", stopSim);
    int clearSharedState = source.indexOf("PlayerStateRegistry.reset();", stopSim);

    assertThat(unregisterAdaptations).isGreaterThan(stopSim);
    assertThat(clearSharedState).isGreaterThan(unregisterAdaptations);
  }
}
