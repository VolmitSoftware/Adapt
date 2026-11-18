package art.arcane.adapt.util.project.redis;

import art.arcane.adapt.api.world.FencedPlayerSnapshot;
import art.arcane.adapt.util.project.redis.codec.DataMessage;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class RedisTransferStaging implements TransferStaging {
  static final long TTL_SECONDS = 60L;
  private static final String KEY_PREFIX = "Adapt:data:v2:stage:";
  private static final int MAGIC = 0x41445054;
  private static final int VERSION = 1;
  private static final int HEADER_BYTES = Integer.BYTES * 3 + Long.BYTES * 6;
  private static final int MAX_RECORD_BYTES = HEADER_BYTES + DataMessage.MAX_JSON_BYTES;
  private static final UUID ZERO_TOKEN = new UUID(0L, 0L);

  private final StatefulRedisConnection<String, byte[]> connection;
  private final RedisReactiveCommands<String, byte[]> commands;

  RedisTransferStaging(StatefulRedisConnection<String, byte[]> connection) {
    this.connection = Objects.requireNonNull(connection);
    commands = connection.reactive();
  }

  static RedisTransferStaging connect(RedisClient client) {
    RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
    return new RedisTransferStaging(Objects.requireNonNull(client).connect(codec));
  }

  @Override
  public CompletableFuture<Void> stage(FencedPlayerSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    String redisKey = key(snapshot.playerId(), snapshot.ownerToken(), snapshot.epoch());
    return CompletableFuture.supplyAsync(() -> encode(snapshot))
        .thenCompose(encoded -> commands.setex(redisKey, TTL_SECONDS, encoded).toFuture())
        .thenApply(result -> {
          if (!"OK".equals(result)) {
            throw new IllegalStateException("Redis rejected the staged player transfer");
          }
          return null;
        });
  }

  @Override
  public CompletableFuture<Optional<FencedPlayerSnapshot>> load(UUID playerId, UUID ownerToken,
                                                                 long epoch) {
    String redisKey = key(playerId, ownerToken, epoch);
    Mono<byte[]> stagedValue = commands.strlen(redisKey).flatMap(length -> {
      if (length == 0L) {
        return Mono.empty();
      }
      if (length < HEADER_BYTES || length > MAX_RECORD_BYTES) {
        return Mono.error(new IllegalStateException(
            "Invalid staged Redis transfer length: " + length));
      }
      return commands.get(redisKey);
    });
    return stagedValue.toFuture().thenApplyAsync(encoded -> encoded == null
        ? Optional.empty()
        : Optional.of(decode(encoded, playerId, ownerToken, epoch)));
  }

  @Override
  public CompletableFuture<Void> acknowledge(UUID playerId, UUID ownerToken, long epoch) {
    return commands.del(key(playerId, ownerToken, epoch))
        .then()
        .toFuture();
  }

  @Override
  public void close() {
    connection.close();
  }

  static byte[] encode(FencedPlayerSnapshot snapshot) {
    validateFence(snapshot.ownerToken(), snapshot.epoch());
    if (snapshot.sequence() < 1L) {
      throw new IllegalArgumentException("Staged transfer sequence must be positive");
    }
    byte[] json = snapshot.json().getBytes(StandardCharsets.UTF_8);
    if (json.length > DataMessage.MAX_JSON_BYTES) {
      throw new IllegalArgumentException(
          "Staged player data exceeds MEDIUMTEXT limit: " + json.length + " bytes");
    }
    ByteBuffer encoded = ByteBuffer.allocate(HEADER_BYTES + json.length);
    encoded.putInt(MAGIC);
    encoded.putInt(VERSION);
    encoded.putLong(snapshot.playerId().getMostSignificantBits());
    encoded.putLong(snapshot.playerId().getLeastSignificantBits());
    encoded.putLong(snapshot.ownerToken().getMostSignificantBits());
    encoded.putLong(snapshot.ownerToken().getLeastSignificantBits());
    encoded.putLong(snapshot.epoch());
    encoded.putLong(snapshot.sequence());
    encoded.putInt(json.length);
    encoded.put(json);
    return encoded.array();
  }

  static FencedPlayerSnapshot decode(byte[] encoded, UUID expectedPlayerId,
                                     UUID expectedOwnerToken, long expectedEpoch) {
    Objects.requireNonNull(encoded);
    validateFence(expectedOwnerToken, expectedEpoch);
    if (encoded.length < HEADER_BYTES || encoded.length > MAX_RECORD_BYTES) {
      throw new IllegalArgumentException("Invalid staged Redis transfer length: " + encoded.length);
    }
    ByteBuffer input = ByteBuffer.wrap(encoded);
    if (input.getInt() != MAGIC || input.getInt() != VERSION) {
      throw new IllegalArgumentException("Invalid staged Redis transfer header");
    }
    UUID playerId = new UUID(input.getLong(), input.getLong());
    UUID ownerToken = new UUID(input.getLong(), input.getLong());
    long epoch = input.getLong();
    long sequence = input.getLong();
    int jsonLength = input.getInt();
    if (!Objects.equals(playerId, expectedPlayerId)
        || !Objects.equals(ownerToken, expectedOwnerToken)
        || epoch != expectedEpoch) {
      throw new IllegalArgumentException("Staged Redis transfer fence does not match its key");
    }
    if (sequence < 1L || jsonLength < 0 || jsonLength > DataMessage.MAX_JSON_BYTES
        || input.remaining() != jsonLength) {
      throw new IllegalArgumentException("Invalid staged Redis transfer payload");
    }
    String json;
    try {
      json = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(input)
          .toString();
    } catch (CharacterCodingException error) {
      throw new IllegalArgumentException("Invalid staged Redis transfer UTF-8", error);
    }
    return new FencedPlayerSnapshot(playerId, ownerToken, epoch, sequence, json);
  }

  static String key(UUID playerId, UUID ownerToken, long epoch) {
    Objects.requireNonNull(playerId);
    validateFence(ownerToken, epoch);
    return KEY_PREFIX + playerId + ':' + ownerToken + ':' + epoch;
  }

  private static void validateFence(UUID ownerToken, long epoch) {
    Objects.requireNonNull(ownerToken);
    if (ZERO_TOKEN.equals(ownerToken) || epoch < 1L) {
      throw new IllegalArgumentException("Staged transfer fence must be positive");
    }
  }
}
