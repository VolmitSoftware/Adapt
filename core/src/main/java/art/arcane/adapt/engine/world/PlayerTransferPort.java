package art.arcane.adapt.engine.world;

import art.arcane.adapt.util.project.redis.codec.ResetNotice;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cross-server player-data transfer and reset fences. Bukkit backs it with Redis when Redis sync is configured;
 * {@link #NONE} means no transfer channel exists, which is the same as Redis sync being disabled.
 */
public interface PlayerTransferPort {
  PlayerTransferPort NONE = new PlayerTransferPort() {
    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public CompletableFuture<Optional<FencedPlayerSnapshot>> awaitTransfers(UUID playerId, UUID predecessorToken,
                                                                           long predecessorEpoch, long timeoutMillis) {
      return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public void acknowledgeTransfer(UUID playerId, UUID predecessorToken, long predecessorEpoch) {
    }

    @Override
    public void retainRetiredTransfer(FencedPlayerSnapshot snapshot) {
    }

    @Override
    public void publishReset(ResetNotice notice) {
    }
  };

  boolean isActive();

  CompletableFuture<Optional<FencedPlayerSnapshot>> awaitTransfers(UUID playerId, UUID predecessorToken,
                                                                  long predecessorEpoch, long timeoutMillis);

  void acknowledgeTransfer(UUID playerId, UUID predecessorToken, long predecessorEpoch);

  void retainRetiredTransfer(FencedPlayerSnapshot snapshot);

  void publishReset(ResetNotice notice);
}
