package art.arcane.adapt.util.project.redis;

import art.arcane.adapt.api.world.FencedPlayerSnapshot;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

interface TransferStaging extends AutoCloseable {
  CompletableFuture<Void> stage(FencedPlayerSnapshot snapshot);

  CompletableFuture<Optional<FencedPlayerSnapshot>> load(UUID playerId, UUID ownerToken,
                                                          long epoch);

  CompletableFuture<Void> acknowledge(UUID playerId, UUID ownerToken, long epoch);

  @Override
  void close();
}

final class DisabledTransferStaging implements TransferStaging {
  @Override
  public CompletableFuture<Void> stage(FencedPlayerSnapshot snapshot) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Optional<FencedPlayerSnapshot>> load(UUID playerId, UUID ownerToken,
                                                                 long epoch) {
    return CompletableFuture.completedFuture(Optional.empty());
  }

  @Override
  public CompletableFuture<Void> acknowledge(UUID playerId, UUID ownerToken, long epoch) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void close() {
  }
}

final class UnavailableTransferStaging implements TransferStaging {
  private final Throwable failure;

  UnavailableTransferStaging(Throwable failure) {
    this.failure = failure;
  }

  @Override
  public CompletableFuture<Void> stage(FencedPlayerSnapshot snapshot) {
    return failed();
  }

  @Override
  public CompletableFuture<Optional<FencedPlayerSnapshot>> load(UUID playerId, UUID ownerToken,
                                                                 long epoch) {
    return failed();
  }

  @Override
  public CompletableFuture<Void> acknowledge(UUID playerId, UUID ownerToken, long epoch) {
    return failed();
  }

  @Override
  public void close() {
  }

  private <T> CompletableFuture<T> failed() {
    return CompletableFuture.failedFuture(
        new IllegalStateException("Redis transfer staging is unavailable", failure));
  }
}
