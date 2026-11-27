package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.engine.world.FencedPlayerSnapshot;
import art.arcane.adapt.engine.world.PlayerTransferPort;
import art.arcane.adapt.util.project.redis.RedisSync;
import art.arcane.adapt.util.project.redis.codec.ResetNotice;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BukkitPlayerTransferPort implements PlayerTransferPort {
  private static RedisSync redis() {
    Adapt plugin = Adapt.instance;
    return plugin == null ? null : plugin.getRedisSync();
  }

  @Override
  public boolean isActive() {
    return redis() != null;
  }

  @Override
  public CompletableFuture<Optional<FencedPlayerSnapshot>> awaitTransfers(UUID playerId, UUID predecessorToken,
                                                                         long predecessorEpoch, long timeoutMillis) {
    RedisSync redisSync = redis();
    if (redisSync == null) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    return redisSync.awaitTransfers(playerId, predecessorToken, predecessorEpoch, timeoutMillis);
  }

  @Override
  public void acknowledgeTransfer(UUID playerId, UUID predecessorToken, long predecessorEpoch) {
    RedisSync redisSync = redis();
    if (redisSync != null) {
      redisSync.acknowledgeTransfer(playerId, predecessorToken, predecessorEpoch);
    }
  }

  @Override
  public void retainRetiredTransfer(FencedPlayerSnapshot snapshot) {
    RedisSync redisSync = redis();
    if (redisSync != null) {
      redisSync.retainRetiredTransfer(snapshot);
    }
  }

  @Override
  public void publishReset(ResetNotice notice) {
    RedisSync redisSync = redis();
    if (redisSync != null) {
      redisSync.publishReset(notice);
    }
  }
}
