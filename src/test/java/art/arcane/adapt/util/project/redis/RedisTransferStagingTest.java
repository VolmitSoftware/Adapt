package art.arcane.adapt.util.project.redis;

import art.arcane.adapt.api.world.FencedPlayerSnapshot;
import art.arcane.adapt.util.project.redis.codec.DataMessage;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTransferStagingTest {
  private StatefulRedisConnection<String, byte[]> connection;
  private RedisReactiveCommands<String, byte[]> commands;
  private RedisTransferStaging staging;

  @BeforeEach
  void createStaging() {
    connection = mock(StatefulRedisConnection.class);
    commands = mock(RedisReactiveCommands.class);
    when(connection.reactive()).thenReturn(commands);
    staging = new RedisTransferStaging(connection);
  }

  @Test
  void stagesTheExactFenceWithABoundedTtlAndBinaryEnvelope() throws Exception {
    FencedPlayerSnapshot snapshot = snapshot("{\"staged\":true}");
    when(commands.setex(anyString(), eq(RedisTransferStaging.TTL_SECONDS), any(byte[].class)))
        .thenReturn(Mono.just("OK"));

    staging.stage(snapshot).get(1L, TimeUnit.SECONDS);

    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<byte[]> value = ArgumentCaptor.forClass(byte[].class);
    verify(commands).setex(
        key.capture(), eq(RedisTransferStaging.TTL_SECONDS), value.capture());
    assertThat(key.getValue()).isEqualTo(RedisTransferStaging.key(
        snapshot.playerId(), snapshot.ownerToken(), snapshot.epoch()));
    assertThat(RedisTransferStaging.decode(
        value.getValue(), snapshot.playerId(), snapshot.ownerToken(), snapshot.epoch()))
        .isEqualTo(snapshot);
  }

  @Test
  void rejectsStagedDataThatDoesNotMatchItsExactFenceKey() {
    FencedPlayerSnapshot snapshot = snapshot("{}");
    byte[] encoded = RedisTransferStaging.encode(snapshot);

    assertThatThrownBy(() -> RedisTransferStaging.decode(
        encoded, snapshot.playerId(), UUID.randomUUID(), snapshot.epoch()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match its key");
  }

  @Test
  void enforcesTheExactMediumTextByteCeiling() {
    FencedPlayerSnapshot maximum = snapshot("a".repeat(DataMessage.MAX_JSON_BYTES));
    FencedPlayerSnapshot oversize = snapshot("a".repeat(DataMessage.MAX_JSON_BYTES + 1));

    assertThat(RedisTransferStaging.encode(maximum)).isNotEmpty();
    assertThatThrownBy(() -> RedisTransferStaging.encode(oversize))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MEDIUMTEXT limit");
  }

  @Test
  void rejectsOversizeRedisValuesBeforeFetchingTheirContents() {
    FencedPlayerSnapshot expected = snapshot("{}");
    String key = RedisTransferStaging.key(
        expected.playerId(), expected.ownerToken(), expected.epoch());
    when(commands.strlen(key)).thenReturn(Mono.just(Long.MAX_VALUE));

    assertThatThrownBy(() -> staging.load(
        expected.playerId(), expected.ownerToken(), expected.epoch())
        .get(1L, TimeUnit.SECONDS))
        .hasRootCauseMessage("Invalid staged Redis transfer length: " + Long.MAX_VALUE);
    verify(commands, never()).get(key);
  }

  @Test
  void missingStageReturnsEmptyAndAcknowledgementDeletesTheExactKey() throws Exception {
    FencedPlayerSnapshot expected = snapshot("{}");
    String key = RedisTransferStaging.key(
        expected.playerId(), expected.ownerToken(), expected.epoch());
    when(commands.strlen(key)).thenReturn(Mono.just(0L));
    when(commands.del(key)).thenReturn(Mono.just(1L));

    Optional<FencedPlayerSnapshot> loaded = staging.load(
        expected.playerId(), expected.ownerToken(), expected.epoch())
        .get(1L, TimeUnit.SECONDS);
    staging.acknowledge(expected.playerId(), expected.ownerToken(), expected.epoch())
        .get(1L, TimeUnit.SECONDS);

    assertThat(loaded).isEmpty();
    verify(commands).del(key);
  }

  private static FencedPlayerSnapshot snapshot(String json) {
    return new FencedPlayerSnapshot(
        UUID.randomUUID(), UUID.randomUUID(), 7L, 11L, json);
  }
}
