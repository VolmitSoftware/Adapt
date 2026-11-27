package art.arcane.adapt.engine.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerDataClaimFenceTest {
  @Test
  void preloginClaimThatReadStorageBeforeTheQuitSaveIsRejected() {
    UUID playerId = UUID.randomUUID();
    PlayerDataClaimFence fence = new PlayerDataClaimFence();
    PlayerData stale = new PlayerData();
    PlayerData committed = new PlayerData();
    AtomicReference<PlayerData> storage = new AtomicReference<>(stale);
    Deque<Runnable> claimThread = new ArrayDeque<>();
    ConcurrentMap<UUID, CompletableFuture<PlayerDataClaimFence.Claim>> activeClaims = new ConcurrentHashMap<>();
    Function<UUID, LoadedPlayerData> storageLoader = (UUID ignored) -> LoadedPlayerData.inspected(storage.get());

    CompletableFuture<PlayerDataClaimFence.Claim> prelogin = AdaptServer.beginPlayerDataClaim(
        playerId, activeClaims, claimThread::addLast, new AtomicBoolean(true),
        (UUID id) -> AdaptServer.fencedClaim(fence, id, storageLoader));
    claimThread.removeFirst().run();

    storage.set(committed);
    fence.revoke(playerId);

    PlayerDataClaimFence.Claim staleClaim = prelogin.join();
    assertThat(staleClaim.data().data()).isSameAs(stale);
    assertThat(fence.close(playerId, staleClaim.ticket())).isFalse();

    CompletableFuture<PlayerDataClaimFence.Claim> retry = AdaptServer.beginPlayerDataClaim(
        playerId, activeClaims, Runnable::run, new AtomicBoolean(true),
        (UUID id) -> AdaptServer.fencedClaim(fence, id, storageLoader));
    PlayerDataClaimFence.Claim freshClaim = retry.join();
    assertThat(freshClaim.data().data()).isSameAs(committed);
    assertThat(fence.close(playerId, freshClaim.ticket())).isTrue();
  }

  @Test
  void claimOpenedAfterRetirementIsAcceptedOnce() {
    UUID playerId = UUID.randomUUID();
    PlayerDataClaimFence fence = new PlayerDataClaimFence();
    fence.revoke(playerId);

    PlayerDataClaimFence.Claim claim = AdaptServer.fencedClaim(
        fence, playerId, (UUID ignored) -> LoadedPlayerData.inspected(new PlayerData()));

    assertThat(fence.close(playerId, claim.ticket())).isTrue();
    assertThat(fence.close(playerId, claim.ticket())).isFalse();
  }

  @Test
  void retiringOnePlayerDoesNotRevokeAnotherPlayersClaim() {
    UUID retiring = UUID.randomUUID();
    UUID joining = UUID.randomUUID();
    PlayerDataClaimFence fence = new PlayerDataClaimFence();
    PlayerDataClaimFence.Claim claim = AdaptServer.fencedClaim(
        fence, joining, (UUID ignored) -> LoadedPlayerData.inspected(new PlayerData()));

    fence.revoke(retiring);

    assertThat(fence.close(joining, claim.ticket())).isTrue();
  }
}
