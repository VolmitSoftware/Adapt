package art.arcane.adapt.util.project.redis;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.FencedPlayerSnapshot;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.project.redis.codec.Codec;
import art.arcane.adapt.util.project.redis.codec.DataMessage;
import art.arcane.adapt.util.project.redis.codec.DataRequest;
import art.arcane.adapt.util.project.redis.codec.Message;
import art.arcane.adapt.util.project.redis.codec.ResetNotice;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.api.reactive.ChannelMessage;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RedisSync implements AutoCloseable {
  private static final int MAX_TRANSFER_CANDIDATES = 8;
  private static final int MAX_TRANSFER_REQUEST_ATTEMPTS = 3;
  private static final long CACHE_MAXIMUM_SIZE = 8_192L;
  private static final long CACHE_EXPIRY_MINUTES = 1L;
  private static final long MIN_TRANSFER_RETRY_MILLIS = 50L;
  private static final long MAX_TRANSFER_WAIT_MILLIS = 3_000L;

  private final RedisClient redisClient;
  private final RedisPubSubReactiveCommands<String, Message> pubSub;
  private final TransferStaging staging;
  private final Cache<UUID, TransferState> transfers = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .build();
  private final Cache<UUID, FencedPlayerSnapshot> retiredTransfers = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .build();
  private final Cache<UUID, Boolean> resetOperations = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .build();
  private final Cache<StagedTransferKey, CompletableFuture<Void>> stagingWrites = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .build();

  public RedisSync() {
    if (!AdaptConfig.get().getRedis().isEnabled() || !AdaptConfig.get().getSql().isEnabled()) {
      redisClient = null;
      pubSub = null;
      staging = new DisabledTransferStaging();
      return;
    }

    redisClient = AdaptConfig.get().getRedis().createClient();
    pubSub = redisClient.connectPubSub(Codec.INSTANCE).reactive();
    staging = connectStaging(redisClient);
    pubSub.subscribe(Codec.CHANNEL).subscribe(unused -> {
    }, this::reportSubscriptionFailure);
    pubSub.observeChannels().subscribe(this::update, this::reportSubscriptionFailure);
  }

  RedisSync(RedisClient redisClient, RedisPubSubReactiveCommands<String, Message> pubSub,
            TransferStaging staging) {
    this.redisClient = redisClient;
    this.pubSub = pubSub;
    this.staging = Objects.requireNonNull(staging);
  }

  public CompletableFuture<Optional<FencedPlayerSnapshot>> awaitTransfers(
      @NonNull UUID playerId,
      @NonNull UUID predecessorToken,
      long predecessorEpoch,
      long timeoutMillis
  ) {
    if (predecessorEpoch < 1L) {
      throw new IllegalArgumentException("Predecessor fence epoch must be positive");
    }
    if (pubSub == null) {
      return CompletableFuture.completedFuture(Optional.empty());
    }

    long boundedTimeout = Math.max(0L, Math.min(timeoutMillis, MAX_TRANSFER_WAIT_MILLIS));
    FenceIdentity expected = new FenceIdentity(predecessorToken, predecessorEpoch);
    if (boundedTimeout == 0L) {
      return CompletableFuture.completedFuture(Optional.empty());
    }

    UUID requestId = UUID.randomUUID();
    TransferState state = transferState(playerId);
    if (!state.beginAwait(requestId, expected)) {
      return CompletableFuture.completedFuture(Optional.empty());
    }

    CompletableFuture<Optional<FencedPlayerSnapshot>> completion = new CompletableFuture<>();
    String replyChannel = Codec.replyChannel(requestId);
    long retryMillis = Math.max(
        MIN_TRANSFER_RETRY_MILLIS,
        boundedTimeout / MAX_TRANSFER_REQUEST_ATTEMPTS
    );
    CompletableFuture.delayedExecutor(boundedTimeout, TimeUnit.MILLISECONDS).execute(
        () -> completeTransfer(playerId, state, replyChannel, completion)
    );
    subscribeForTransfer(playerId, requestId, state, replyChannel, retryMillis, completion);
    return completion;
  }

  public void publishReset(@NonNull ResetNotice notice) {
    transfers.invalidate(notice.uuid());
    retiredTransfers.invalidate(notice.uuid());
    if (!rememberReset(notice.operationId()) || pubSub == null) {
      return;
    }
    publishMessage(notice);
  }

  public void retainRetiredTransfer(@NonNull FencedPlayerSnapshot snapshot) {
    retiredTransfers.put(snapshot.playerId(), snapshot);
  }

  public void acknowledgeTransfer(@NonNull UUID playerId, @NonNull UUID predecessorToken,
                                  long predecessorEpoch) {
    CompletableFuture<Void> acknowledgement;
    try {
      acknowledgement = Objects.requireNonNull(
          staging.acknowledge(playerId, predecessorToken, predecessorEpoch));
    } catch (RuntimeException error) {
      reportStagingFailure("acknowledge", playerId, error);
      return;
    }
    acknowledgement.whenComplete((unused, error) -> {
      if (error != null) {
        reportStagingFailure("acknowledge", playerId, error);
      }
    });
  }

  @Override
  public void close() throws Exception {
    try {
      staging.close();
    } finally {
      if (redisClient != null) {
        redisClient.close();
      }
    }
  }

  void receive(@Nullable Message raw) {
    if (raw instanceof DataRequest request) {
      receiveRequest(request);
    } else if (raw instanceof DataMessage message) {
      receiveData(message);
    } else if (raw instanceof ResetNotice notice) {
      receiveReset(notice);
    }
  }

  private void update(@NotNull ChannelMessage<@NotNull String, @Nullable Message> channelMessage) {
    String channel = channelMessage.getChannel();
    Message message = channelMessage.getMessage();
    if (Codec.CHANNEL.equals(channel)) {
      try {
        receive(message);
      } catch (Throwable error) {
        reportProcessingFailure(error);
      }
      return;
    }
    if (Codec.isReplyChannel(channel) && message instanceof DataMessage dataMessage) {
      try {
        receiveData(dataMessage);
      } catch (Throwable error) {
        reportProcessingFailure(error);
      }
    }
  }

  private void receiveRequest(DataRequest request) {
    Adapt plugin = Adapt.instance;
    AdaptServer server = plugin == null ? null : plugin.getAdaptServer();
    if (server == null) {
      return;
    }

    AdaptPlayer adaptPlayer = server.getOnlineAdaptPlayer(request.uuid());
    if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
      publishRetiredTransfer(request);
      return;
    }

    Player player = adaptPlayer.getPlayer();
    J.runEntity(player, () -> publishTransfer(request, adaptPlayer, player));
  }

  private void receiveData(DataMessage message) {
    TransferState state = transfers.getIfPresent(message.uuid());
    if (state != null) {
      state.accept(message);
    }
  }

  private void receiveReset(ResetNotice notice) {
    transfers.invalidate(notice.uuid());
    retiredTransfers.invalidate(notice.uuid());
    if (!rememberReset(notice.operationId())) {
      return;
    }

    Adapt plugin = Adapt.instance;
    AdaptServer server = plugin == null ? null : plugin.getAdaptServer();
    if (server == null) {
      return;
    }

    try {
      server.applyRemoteReset(
          notice.uuid(),
          notice.operationId(),
          notice.epoch(),
          notice.purge()
      );
    } catch (RuntimeException | Error error) {
      resetOperations.invalidate(notice.operationId());
      throw error;
    }
  }

  private void publishTransfer(DataRequest request, AdaptPlayer expected, Player player) {
    Adapt plugin = Adapt.instance;
    AdaptServer server = plugin == null ? null : plugin.getAdaptServer();
    if (server == null) {
      return;
    }
    FencedPlayerSnapshot snapshot = server.retirePlayerForTransfer(
        request.uuid(), request.ownerToken(), request.epoch(), expected, player);
    if (snapshot == null) {
      publishRetiredTransfer(request);
      return;
    }

    publishTransferSnapshot(request, snapshot);
  }

  private void publishRetiredTransfer(DataRequest request) {
    FencedPlayerSnapshot snapshot = retiredTransfers.getIfPresent(request.uuid());
    if (snapshot != null && snapshot.belongsTo(request.ownerToken(), request.epoch())) {
      publishTransferSnapshot(request, snapshot);
    }
  }

  private void publishTransferSnapshot(DataRequest request, FencedPlayerSnapshot snapshot) {
    stageTransfer(snapshot).whenComplete((unused, error) -> {
      if (error != null) {
        reportStagingFailure("stage", snapshot.playerId(), error);
      }
      publishDirectTransferSnapshot(request, snapshot);
    });
  }

  private void publishDirectTransferSnapshot(DataRequest request, FencedPlayerSnapshot snapshot) {
    publishMessage(Codec.replyChannel(request.requestId()), new DataMessage(
        snapshot.playerId(),
        request.requestId(),
        snapshot.ownerToken(),
        snapshot.epoch(),
        snapshot.sequence(),
        snapshot.json()
    ));
  }

  private TransferState transferState(UUID playerId) {
    return transfers.get(playerId, unused -> new TransferState());
  }

  private void subscribeForTransfer(UUID playerId, UUID requestId, TransferState state,
                                    String replyChannel, long retryMillis,
                                    CompletableFuture<Optional<FencedPlayerSnapshot>> completion) {
    pubSub.subscribe(replyChannel).subscribe(unused -> {
    }, error -> failTransfer(playerId, state, replyChannel, completion, error), () -> {
      if (completion.isDone()) {
        unsubscribe(replyChannel);
        return;
      }
      publishTransferRequest(
          playerId, requestId, state, replyChannel, retryMillis, completion);
    });
  }

  private void publishTransferRequest(UUID playerId, UUID requestId, TransferState state,
                                      String replyChannel, long retryMillis,
                                      CompletableFuture<Optional<FencedPlayerSnapshot>> completion) {
    if (completion.isDone()) {
      return;
    }
    DataRequest request = state.nextRequest(playerId, requestId);
    if (request == null) {
      return;
    }
    pubSub.publish(Codec.CHANNEL, request).subscribe(ignored -> {
    }, error -> failTransfer(playerId, state, replyChannel, completion, error), () ->
        CompletableFuture.delayedExecutor(retryMillis, TimeUnit.MILLISECONDS).execute(
            () -> publishTransferRequest(
                playerId, requestId, state, replyChannel, retryMillis, completion)
        ));
  }

  private void completeTransfer(UUID playerId, TransferState state, String replyChannel,
                                CompletableFuture<Optional<FencedPlayerSnapshot>> completion) {
    if (completion.isDone()) {
      return;
    }
    Optional<FencedPlayerSnapshot> selected = state.consume();
    transfers.asMap().remove(playerId, state);
    unsubscribe(replyChannel);
    if (selected.isPresent()) {
      completion.complete(selected);
      return;
    }
    try {
      staging.load(playerId, state.expectedOwnerToken(), state.expectedEpoch())
          .whenComplete((staged, error) -> completeStagedTransfer(
              playerId, state, completion, staged, error));
    } catch (RuntimeException error) {
      completion.completeExceptionally(error);
    }
  }

  private void failTransfer(UUID playerId, TransferState state, String replyChannel,
                            CompletableFuture<Optional<FencedPlayerSnapshot>> completion,
                            Throwable error) {
    if (completion.completeExceptionally(error)) {
      state.consume();
      transfers.asMap().remove(playerId, state);
      unsubscribe(replyChannel);
    }
  }

  private void unsubscribe(String replyChannel) {
    pubSub.unsubscribe(replyChannel).subscribe(unused -> {
    }, error -> {
      Adapt.warn("Failed to unsubscribe from Redis transfer channel: " + error.getMessage());
      Adapt.error(error);
    });
  }

  private boolean rememberReset(UUID operationId) {
    return resetOperations.asMap().putIfAbsent(operationId, Boolean.TRUE) == null;
  }

  private void publishMessage(Message message) {
    publishMessage(Codec.CHANNEL, message);
  }

  private void publishMessage(String channel, Message message) {
    try {
      pubSub.publish(channel, message).subscribe(unused -> {
      }, this::reportPublishFailure);
    } catch (RuntimeException error) {
      reportPublishFailure(error);
    }
  }

  private void reportPublishFailure(Throwable error) {
    Adapt.warn("Failed to publish Redis synchronization message: " + error.getMessage());
    Adapt.error(error);
  }

  private void reportSubscriptionFailure(Throwable error) {
    Adapt.warn("Redis synchronization subscription failed: " + error.getMessage());
    Adapt.error(error);
  }

  private void reportProcessingFailure(Throwable error) {
    Adapt.warn("Failed to process Redis synchronization message: " + error.getMessage());
    Adapt.error(error);
  }

  private CompletableFuture<Void> stageTransfer(FencedPlayerSnapshot snapshot) {
    StagedTransferKey key = new StagedTransferKey(
        snapshot.playerId(), snapshot.ownerToken(), snapshot.epoch());
    CompletableFuture<Void> staged = stagingWrites.asMap().computeIfAbsent(key, unused -> {
      CompletableFuture<Void> candidate;
      try {
        candidate = Objects.requireNonNull(staging.stage(snapshot));
      } catch (RuntimeException error) {
        candidate = CompletableFuture.failedFuture(error);
      }
      return candidate;
    });
    staged.whenComplete((ignored, error) -> {
      if (error != null) {
        stagingWrites.asMap().remove(key, staged);
      }
    });
    return staged;
  }

  private void completeStagedTransfer(UUID playerId, TransferState state,
                                      CompletableFuture<Optional<FencedPlayerSnapshot>> completion,
                                      Optional<FencedPlayerSnapshot> staged, Throwable error) {
    if (error != null) {
      completion.completeExceptionally(error);
      return;
    }
    try {
      Optional<FencedPlayerSnapshot> validated = Objects.requireNonNull(staged)
          .map(snapshot -> validateStagedTransfer(playerId, state, snapshot));
      completion.complete(validated);
    } catch (RuntimeException validationError) {
      completion.completeExceptionally(validationError);
    }
  }

  private FencedPlayerSnapshot validateStagedTransfer(UUID playerId, TransferState state,
                                                       FencedPlayerSnapshot snapshot) {
    if (!playerId.equals(snapshot.playerId())
        || !snapshot.belongsTo(state.expectedOwnerToken(), state.expectedEpoch())
        || snapshot.sequence() < 1L
        || snapshot.json().getBytes(StandardCharsets.UTF_8).length
        > DataMessage.MAX_JSON_BYTES) {
      throw new IllegalStateException("Staged Redis transfer does not match the expected fence");
    }
    return snapshot;
  }

  private void reportStagingFailure(String operation, UUID playerId, Throwable error) {
    Adapt.warn("Failed to " + operation + " Redis transfer staging for " + playerId + ": "
        + error.getMessage());
    Adapt.error(error);
  }

  private static TransferStaging connectStaging(RedisClient redisClient) {
    try {
      return RedisTransferStaging.connect(redisClient);
    } catch (RuntimeException error) {
      Adapt.warn("Failed to initialize Redis transfer staging: " + error.getMessage());
      Adapt.error(error);
      return new UnavailableTransferStaging(error);
    }
  }

  private record StagedTransferKey(UUID playerId, UUID ownerToken, long epoch) {
    private StagedTransferKey {
      Objects.requireNonNull(playerId);
      Objects.requireNonNull(ownerToken);
    }
  }

  private record FenceIdentity(UUID ownerToken, long epoch) {
    private FenceIdentity {
      Objects.requireNonNull(ownerToken);
    }
  }

  private static final class TransferState {
    private final Map<FenceIdentity, FencedPlayerSnapshot> candidates = new HashMap<>();
    private final Set<FenceIdentity> conflicts = new HashSet<>();
    private UUID requestId;
    private FenceIdentity expected;
    private int requestAttempts;
    private boolean awaiting;
    private boolean consumed;

    private synchronized boolean beginAwait(UUID candidateRequestId, FenceIdentity candidateExpected) {
      if (awaiting || consumed) {
        return false;
      }
      requestId = Objects.requireNonNull(candidateRequestId);
      expected = Objects.requireNonNull(candidateExpected);
      candidates.clear();
      conflicts.clear();
      requestAttempts = 0;
      awaiting = true;
      return true;
    }

    private synchronized DataRequest nextRequest(UUID playerId, UUID candidateRequestId) {
      if (!awaiting || consumed || requestAttempts >= MAX_TRANSFER_REQUEST_ATTEMPTS
          || !Objects.equals(requestId, candidateRequestId)
          || candidates.containsKey(expected)) {
        return null;
      }
      requestAttempts++;
      return new DataRequest(playerId, requestId, expected.ownerToken(), expected.epoch());
    }

    private synchronized UUID expectedOwnerToken() {
      return expected.ownerToken();
    }

    private synchronized long expectedEpoch() {
      return expected.epoch();
    }

    private synchronized void accept(DataMessage message) {
      if (!awaiting || consumed || !Objects.equals(requestId, message.requestId())) {
        return;
      }

      FenceIdentity identity = new FenceIdentity(message.ownerToken(), message.epoch());
      if (conflicts.contains(identity)) {
        return;
      }
      FencedPlayerSnapshot existing = candidates.get(identity);
      if (existing != null) {
        if (message.sequence() > existing.sequence()) {
          candidates.put(identity, snapshot(message));
        } else if (message.sequence() == existing.sequence()
            && !message.json().equals(existing.json())) {
          candidates.remove(identity);
          conflicts.add(identity);
          Adapt.warn("Rejected conflicting Redis player snapshots at sequence "
              + message.sequence() + " for " + message.uuid());
        }
        return;
      }

      if (candidates.size() < MAX_TRANSFER_CANDIDATES) {
        candidates.put(identity, snapshot(message));
        return;
      }

      FenceIdentity eviction = evictionCandidate(identity, message.sequence());
      if (eviction != null) {
        candidates.remove(eviction);
        conflicts.remove(eviction);
        candidates.put(identity, snapshot(message));
      }
    }

    private FenceIdentity evictionCandidate(FenceIdentity incoming, long incomingSequence) {
      FenceIdentity lowestIdentity = null;
      long lowestSequence = Long.MAX_VALUE;
      for (Map.Entry<FenceIdentity, FencedPlayerSnapshot> entry : candidates.entrySet()) {
        if (entry.getKey().equals(expected)) {
          continue;
        }
        if (entry.getValue().sequence() < lowestSequence) {
          lowestIdentity = entry.getKey();
          lowestSequence = entry.getValue().sequence();
        }
      }
      if (incoming.equals(expected)) {
        return lowestIdentity;
      }
      return incomingSequence > lowestSequence ? lowestIdentity : null;
    }

    private synchronized Optional<FencedPlayerSnapshot> consume() {
      if (consumed) {
        return Optional.empty();
      }
      consumed = true;
      FencedPlayerSnapshot selected = expected == null ? null : candidates.get(expected);
      candidates.clear();
      conflicts.clear();
      return Optional.ofNullable(selected);
    }

    private static FencedPlayerSnapshot snapshot(DataMessage message) {
      return new FencedPlayerSnapshot(
          message.uuid(),
          message.ownerToken(),
          message.epoch(),
          message.sequence(),
          message.json()
      );
    }
  }
}
