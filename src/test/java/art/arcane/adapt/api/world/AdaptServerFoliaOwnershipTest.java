package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.api.potion.AdaptPotionRegistry;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptServerFoliaOwnershipTest extends AdaptTestBase {
  @AfterEach
  void clearPotionState() {
    AdaptPotionRegistry.reset();
  }

  @Test
  void retiredWrapperRetentionUsesMembershipWithoutReadingTheEntity() {
    UUID playerId = UUID.randomUUID();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    when(plugin.getManager()).thenReturn(mock(AdvancementManager.class));
    AdaptPlayer adaptPlayer = new AdaptPlayer(player, new PlayerData());
    long onlineAt = 10_000L;

    assertThat(adaptPlayer.shouldUnload(onlineAt, true)).isFalse();
    assertThat(adaptPlayer.shouldUnload(onlineAt + 60_001L, false)).isFalse();
    assertThat(AdaptPlayer.retiredRetentionExpired(onlineAt, onlineAt + 60_000L)).isFalse();
    assertThat(AdaptPlayer.retiredRetentionExpired(onlineAt, onlineAt + 60_001L)).isTrue();
    verify(player, never()).isOnline();
  }

  @Test
  void offOwnerPotionRetentionDefersPlayerReadsToTheEntityScheduler() {
    UUID playerId = UUID.randomUUID();
    Player player = mock(Player.class);
    AtomicBoolean entityThread = new AtomicBoolean();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();
    when(player.getUniqueId()).thenReturn(playerId);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.isOwnedByCurrentRegion(same(player))).thenReturn(false);
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), any(Runnable.class)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(1));
            return true;
          });

      CompletableFuture<Boolean> completion = AdaptServer.runOwnedPotionRetention(
          playerId, player, () -> assertThat(entityThread.get()).isTrue());

      assertThat(completion.isDone()).isFalse();
      assertThat(scheduled.get()).isNotNull();
      entityThread.set(true);
      scheduled.get().run();
      entityThread.set(false);

      assertThat(completion.getNow(false)).isTrue();
    }
  }

  @Test
  void retiredEntityCompletesPotionRetentionWithoutWaitingForTimeout() {
    UUID playerId = UUID.randomUUID();
    Player player = mock(Player.class);
    Runnable retention = mock(Runnable.class);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.isOwnedByCurrentRegion(same(player))).thenReturn(false);
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable retired = invocation.getArgument(2);
            retired.run();
            return true;
          });

      CompletableFuture<Boolean> completion = AdaptServer.runOwnedPotionRetention(
          playerId, player, retention);

      assertThat(completion.isDone()).isTrue();
      assertThat(completion.getNow(true)).isFalse();
      verify(retention, never()).run();
    }
  }

  @Test
  void potionRetentionBarrierAggregatesBestEffortFailures() {
    assertThat(AdaptServer.awaitPotionRetention(List.of(), 100L)).isTrue();
    assertThat(AdaptServer.awaitPotionRetention(List.of(
        CompletableFuture.completedFuture(true),
        CompletableFuture.completedFuture(false)
    ), 100L)).isFalse();
  }

  @Test
  void shutdownAwaitsPotionRetentionBeforeRegistryAndPersistenceCleanup() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/api/world/AdaptServer.java"));
    int unregister = source.indexOf("public void unregister()");
    int await = source.indexOf("awaitPotionRetention(potionRetentions", unregister);
    int registry = source.indexOf("skillRegistry.unregister()", unregister);
    int save = source.indexOf("save();", registry);

    assertThat(source)
        .contains("onlineAdaptPlayers.get(entry.getKey()) == player")
        .contains("player.shouldUnload(now, onlineMembership)");
    assertThat(await).isGreaterThan(unregister).isLessThan(registry);
    assertThat(registry).isLessThan(save);
    assertThat(source).doesNotContain("AdaptPotionRegistry.retainActive(Bukkit.getPlayer");
  }
}
