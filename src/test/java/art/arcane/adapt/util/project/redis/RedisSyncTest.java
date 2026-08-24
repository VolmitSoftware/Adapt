package art.arcane.adapt.util.project.redis;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.FencedPlayerSnapshot;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.project.redis.codec.Codec;
import art.arcane.adapt.util.project.redis.codec.DataMessage;
import art.arcane.adapt.util.project.redis.codec.DataRequest;
import art.arcane.adapt.util.project.redis.codec.Message;
import art.arcane.adapt.util.project.redis.codec.ResetNotice;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSyncTest extends AdaptTestBase {
  private RedisPubSubReactiveCommands<String, Message> pubSub;
  private TransferStaging staging;
  private RedisSync redisSync;

  @BeforeEach
  void createRedisSync() {
    RedisClient redisClient = mock(RedisClient.class);
    pubSub = mock(RedisPubSubReactiveCommands.class);
    when(pubSub.publish(anyString(), any(Message.class))).thenReturn(Mono.just(1L));
    when(pubSub.subscribe(anyString())).thenReturn(Mono.empty());
    when(pubSub.unsubscribe(anyString())).thenReturn(Mono.empty());
    staging = mock(TransferStaging.class);
    when(staging.stage(any(FencedPlayerSnapshot.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(staging.load(any(UUID.class), any(UUID.class), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    when(staging.acknowledge(any(UUID.class), any(UUID.class), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));
    redisSync = new RedisSync(redisClient, pubSub, staging);
  }

  @Test
  void capturesAFencedSnapshotOnlyOnTheAuthoritativePlayerOwner() {
    UUID playerId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, ownerToken, 7L, 11L, "{\"source\":true}"
    );
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    Player player = mock(Player.class);
    List<Runnable> ownerTasks = new ArrayList<>();
    CompletableFuture<Void> staged = new CompletableFuture<>();
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(server.retirePlayerForTransfer(
        playerId, ownerToken, 7L, adaptPlayer, player)).thenReturn(snapshot);
    when(staging.stage(snapshot)).thenReturn(staged);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTasks.add(invocation.getArgument(1));
        return true;
      });

      redisSync.receive(new DataRequest(playerId, requestId, ownerToken, 7L));

      verify(server, never()).retirePlayerForTransfer(
          playerId, ownerToken, 7L, adaptPlayer, player);
      assertThat(ownerTasks).hasSize(1);
      ownerTasks.getFirst().run();
    }

    verify(server).retirePlayerForTransfer(playerId, ownerToken, 7L, adaptPlayer, player);
    verify(staging).stage(snapshot);
    verify(pubSub, never()).publish(
        eq(Codec.replyChannel(requestId)), any(DataMessage.class));
    staged.complete(null);
    verify(pubSub).publish(Codec.replyChannel(requestId), new DataMessage(
        playerId, requestId, ownerToken, 7L, 11L, snapshot.json()
    ));
  }

  @Test
  void refusesARuntimeThatLostAuthorityBeforeOwnerExecution() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    DataRequest request = new DataRequest(playerId, UUID.randomUUID(), ownerToken, 3L);
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer original = mock(AdaptPlayer.class);
    Player player = mock(Player.class);
    List<Runnable> ownerTasks = new ArrayList<>();
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(original);
    when(original.isRuntimeReady()).thenReturn(true);
    when(original.getPlayer()).thenReturn(player);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTasks.add(invocation.getArgument(1));
        return true;
      });

      redisSync.receive(request);
      ownerTasks.getFirst().run();
    }

    verify(server).retirePlayerForTransfer(playerId, ownerToken, 3L, original, player);
    verify(pubSub, never()).publish(eq(Codec.replyChannel(request.requestId())), any(DataMessage.class));
  }

  @Test
  void retiredSnapshotAnswersACorrelatedRetryWithoutAnotherOwnerTask() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    DataRequest first = new DataRequest(playerId, UUID.randomUUID(), ownerToken, 4L);
    DataRequest second = new DataRequest(playerId, UUID.randomUUID(), ownerToken, 4L);
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    Player player = mock(Player.class);
    List<Runnable> ownerTasks = new ArrayList<>();
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, ownerToken, 4L, 2L, "{\"latest\":true}"
    );
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer, (AdaptPlayer) null);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(server.retirePlayerForTransfer(
        playerId, ownerToken, 4L, adaptPlayer, player)).thenAnswer(invocation -> {
          redisSync.retainRetiredTransfer(snapshot);
          return snapshot;
        });

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTasks.add(invocation.getArgument(1));
        return true;
      });

      redisSync.receive(first);
      ownerTasks.get(0).run();
      redisSync.receive(second);
    }

    assertThat(ownerTasks).hasSize(1);
    verify(server).retirePlayerForTransfer(playerId, ownerToken, 4L, adaptPlayer, player);
    verify(staging).stage(snapshot);
    verify(pubSub).publish(Codec.replyChannel(first.requestId()), message(first, snapshot));
    verify(pubSub).publish(Codec.replyChannel(second.requestId()), message(second, snapshot));
  }

  @Test
  void stagingFailureStillPublishesTheDirectCorrelatedReply() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    DataRequest request = new DataRequest(playerId, UUID.randomUUID(), ownerToken, 5L);
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, ownerToken, 5L, 8L, "{\"direct\":true}"
    );
    AdaptServer server = mock(AdaptServer.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(null);
    when(staging.stage(snapshot)).thenReturn(CompletableFuture.failedFuture(
        new IllegalStateException("staging unavailable")));
    redisSync.retainRetiredTransfer(snapshot);

    redisSync.receive(request);

    verify(pubSub).publish(Codec.replyChannel(request.requestId()), message(request, snapshot));
  }

  @Test
  void publishesTheRequestOnlyAfterTheReplySubscriptionIsReady() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    CompletableFuture<Void> subscription = new CompletableFuture<>();
    when(pubSub.subscribe(anyString())).thenReturn(Mono.fromFuture(subscription));

    CompletableFuture<Optional<FencedPlayerSnapshot>> awaited = redisSync.awaitTransfers(
        playerId, ownerToken, 6L, 40L);

    verify(pubSub, never()).publish(eq(Codec.CHANNEL), any(DataRequest.class));
    subscription.complete(null);
    DataRequest request = publishedRequest();

    assertThat(request.uuid()).isEqualTo(playerId);
    assertThat(request.ownerToken()).isEqualTo(ownerToken);
    assertThat(request.epoch()).isEqualTo(6L);
    assertThat(awaited.get(1L, TimeUnit.SECONDS)).isEmpty();
  }

  @Test
  void retriesTheSameRequestDuringTheBoundedWaitAfterALostReply() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    AtomicInteger requestCount = new AtomicInteger();
    CompletableFuture<DataRequest> repeatedRequest = new CompletableFuture<>();
    when(pubSub.publish(eq(Codec.CHANNEL), any(Message.class))).thenAnswer(invocation -> {
      Message message = invocation.getArgument(1);
      if (message instanceof DataRequest request && requestCount.incrementAndGet() == 2) {
        repeatedRequest.complete(request);
      }
      return Mono.just(1L);
    });

    CompletableFuture<Optional<FencedPlayerSnapshot>> awaited = redisSync.awaitTransfers(
        playerId, ownerToken, 6L, 250L);
    DataRequest retry = repeatedRequest.get(1L, TimeUnit.SECONDS);
    redisSync.receive(new DataMessage(
        playerId, retry.requestId(), ownerToken, 6L, 9L, "{\"retry\":true}"
    ));

    Optional<FencedPlayerSnapshot> selected = awaited.get(1L, TimeUnit.SECONDS);
    ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
    verify(pubSub, times(2)).publish(eq(Codec.CHANNEL), messages.capture());
    List<Message> requests = messages.getAllValues();
    DataRequest initial = (DataRequest) requests.getFirst();

    assertThat(retry.requestId()).isEqualTo(initial.requestId());
    assertThat(retry.ownerToken()).isEqualTo(initial.ownerToken());
    assertThat(retry.epoch()).isEqualTo(initial.epoch());
    assertThat(selected).isPresent();
    assertThat(selected.orElseThrow().sequence()).isEqualTo(9L);
  }

  @Test
  void timeoutRecoversTheExactDurablyStagedFenceWhenEveryReplyIsLost() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, ownerToken, 13L, 21L, "{\"durable\":true}"
    );
    when(staging.load(playerId, ownerToken, 13L))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(snapshot)));

    Optional<FencedPlayerSnapshot> selected = redisSync
        .awaitTransfers(playerId, ownerToken, 13L, 40L)
        .get(1L, TimeUnit.SECONDS);

    assertThat(selected).contains(snapshot);
    verify(staging).load(playerId, ownerToken, 13L);
  }

  @Test
  void timeoutRejectsAStagedSnapshotThatDoesNotMatchTheExactFence() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    FencedPlayerSnapshot wrong = new FencedPlayerSnapshot(
        playerId, UUID.randomUUID(), 13L, 21L, "{\"wrong\":true}"
    );
    when(staging.load(playerId, ownerToken, 13L))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(wrong)));

    CompletableFuture<Optional<FencedPlayerSnapshot>> awaited = redisSync.awaitTransfers(
        playerId, ownerToken, 13L, 40L);

    assertThatThrownBy(() -> awaited.get(1L, TimeUnit.SECONDS))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("Staged Redis transfer does not match the expected fence");
  }

  @Test
  void acknowledgesTheExactPredecessorFenceAsynchronously() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();

    redisSync.acknowledgeTransfer(playerId, ownerToken, 17L);

    verify(staging).acknowledge(playerId, ownerToken, 17L);
  }

  @Test
  void gathersDistinctFencesAndReturnsTheGreatestExpectedSequence() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID expectedToken = UUID.randomUUID();
    long expectedEpoch = 12L;
    CompletableFuture<Optional<FencedPlayerSnapshot>> awaited = redisSync.awaitTransfers(
        playerId, expectedToken, expectedEpoch, 40L
    );
    DataRequest request = publishedRequest();
    assertThat(request.ownerToken()).isEqualTo(expectedToken);
    assertThat(request.epoch()).isEqualTo(expectedEpoch);

    for (int index = 0; index < 8; index++) {
      redisSync.receive(new DataMessage(
          playerId,
          request.requestId(),
          UUID.randomUUID(),
          index + 1L,
          100L + index,
          "{\"wrong\":" + index + "}"
      ));
    }
    redisSync.receive(new DataMessage(
        playerId, request.requestId(), expectedToken, expectedEpoch, 2L, "{\"sequence\":2}"
    ));
    redisSync.receive(new DataMessage(
        playerId, request.requestId(), expectedToken, expectedEpoch, 6L, "{\"sequence\":6}"
    ));
    redisSync.receive(new DataMessage(
        playerId, request.requestId(), expectedToken, expectedEpoch, 4L, "{\"sequence\":4}"
    ));

    Optional<FencedPlayerSnapshot> selected = awaited.get(1L, TimeUnit.SECONDS);

    assertThat(selected).isPresent();
    assertThat(selected.orElseThrow().ownerToken()).isEqualTo(expectedToken);
    assertThat(selected.orElseThrow().epoch()).isEqualTo(expectedEpoch);
    assertThat(selected.orElseThrow().sequence()).isEqualTo(6L);
    assertThat(selected.orElseThrow().json()).contains("6");
  }

  @Test
  void wrongRequestAndWrongFenceCannotSuppressTheExpectedCandidate() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID expectedToken = UUID.randomUUID();
    CompletableFuture<Optional<FencedPlayerSnapshot>> awaited = redisSync.awaitTransfers(
        playerId, expectedToken, 3L, 30L
    );
    DataRequest request = publishedRequest();
    redisSync.receive(new DataMessage(
        playerId, UUID.randomUUID(), expectedToken, 3L, 100L, "{\"staleRequest\":true}"
    ));
    redisSync.receive(new DataMessage(
        playerId, request.requestId(), UUID.randomUUID(), 3L, 200L, "{\"wrongOwner\":true}"
    ));
    redisSync.receive(new DataMessage(
        playerId, request.requestId(), expectedToken, 3L, 5L, "{\"valid\":true}"
    ));

    Optional<FencedPlayerSnapshot> selected = awaited.get(1L, TimeUnit.SECONDS);

    assertThat(selected).isPresent();
    assertThat(selected.orElseThrow().sequence()).isEqualTo(5L);
  }

  @Test
  void zeroTimeoutDoesNotPublishOrConsumeUnsolicitedData() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();

    Optional<FencedPlayerSnapshot> selected = redisSync
        .awaitTransfers(playerId, ownerToken, 9L, 0L)
        .get(1L, TimeUnit.SECONDS);

    assertThat(selected).isEmpty();
    verify(pubSub, never()).publish(eq(Codec.CHANNEL), any(DataRequest.class));
  }

  @Test
  void rejectsNegativeFenceEpochsAtTheAwaitBoundary() {
    assertThatThrownBy(() -> redisSync.awaitTransfers(
        UUID.randomUUID(), UUID.randomUUID(), -1L, 1L
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void publishesAndDeduplicatesResetOperations() {
    ResetNotice notice = new ResetNotice(
        UUID.randomUUID(), UUID.randomUUID(), 8L, false
    );

    redisSync.publishReset(notice);
    redisSync.publishReset(notice);

    verify(pubSub).publish(Codec.CHANNEL, notice);
  }

  @Test
  void dispatchesEachRemoteResetOperationOnce() {
    AdaptServer server = mock(AdaptServer.class);
    ResetNotice notice = new ResetNotice(
        UUID.randomUUID(), UUID.randomUUID(), 10L, true
    );
    when(plugin.getAdaptServer()).thenReturn(server);

    redisSync.receive(notice);
    redisSync.receive(notice);

    verify(server).applyRemoteReset(
        notice.uuid(), notice.operationId(), notice.epoch(), notice.purge()
    );
  }

  private DataRequest publishedRequest() {
    ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
    verify(pubSub).publish(eq(Codec.CHANNEL), message.capture());
    assertThat(message.getValue()).isInstanceOf(DataRequest.class);
    return (DataRequest) message.getValue();
  }

  private static DataMessage message(DataRequest request, FencedPlayerSnapshot snapshot) {
    return new DataMessage(
        snapshot.playerId(),
        request.requestId(),
        snapshot.ownerToken(),
        snapshot.epoch(),
        snapshot.sequence(),
        snapshot.json()
    );
  }
}
