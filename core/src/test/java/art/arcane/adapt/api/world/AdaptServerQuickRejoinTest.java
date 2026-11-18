package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.event.player.PlayerPreLoginEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptServerQuickRejoinTest {
  private static final String MARKER = "qa.rejoin.marker";

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Rejoiner", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void rejoinAfterPreloginReadStaleStorageBeforeTheQuitSaveKeepsTheLatestData() throws Exception {
    AdaptPlayer first = adapt.join(player);
    first.getData().addStat(MARKER, 1D);
    first.saveNow();
    first.getData().addStat(MARKER, 1D);

    AdaptServer server = adapt.core().getAdaptServer();
    UUID playerId = player.uuid();
    synchronized (playerOperationLock(server, playerId)) {
      adapt.post(new PlayerPreLoginEvent(playerId, player.name(), true));
      awaitReal(() -> hasOpenClaimTicket(server, playerId)
          && !activeClaims(server).containsKey(playerId), 10_000L);

      adapt.post(new PlayerQuitEvent(player));
    }

    AdaptPlayer second = adapt.join(player);

    assertThat(second).isNotSameAs(first);
    assertThat(second.getData().getStat(MARKER)).isEqualTo(2D);
  }

  private static Object playerOperationLock(AdaptServer server, UUID playerId) throws Exception {
    Method method = AdaptServer.class.getDeclaredMethod("playerOperationLock", UUID.class);
    method.setAccessible(true);
    return method.invoke(server, playerId);
  }

  private static Map<?, ?> activeClaims(AdaptServer server) {
    return (Map<?, ?>) read(server, AdaptServer.class, "playerDataClaims");
  }

  private static boolean hasOpenClaimTicket(AdaptServer server, UUID playerId) {
    Object fence = read(server, AdaptServer.class, "playerDataClaimFence");
    Map<?, ?> tickets = (Map<?, ?>) read(fence, PlayerDataClaimFence.class, "openTickets");
    return tickets.containsKey(playerId);
  }

  private static Object read(Object owner, Class<?> type, String name) {
    try {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(owner);
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
  }

  private static void awaitReal(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Condition not reached within " + timeoutMillis + " ms");
      }
      Thread.sleep(10L);
    }
  }
}
