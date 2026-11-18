package art.arcane.adapt.util.project.redis.codec;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record DataMessage(
    @NonNull UUID uuid,
    @NonNull UUID requestId,
    @NonNull UUID ownerToken,
    long epoch,
    long sequence,
    @NonNull String json
) implements Message {
  public static final int MAX_JSON_BYTES = 16_777_215;
  private static final UUID ZERO_OWNER_TOKEN = new UUID(0L, 0L);

  public DataMessage {
    Objects.requireNonNull(uuid);
    Objects.requireNonNull(requestId);
    Objects.requireNonNull(ownerToken);
    Objects.requireNonNull(json);
    if (ZERO_OWNER_TOKEN.equals(ownerToken) || epoch < 1L) {
      throw new IllegalArgumentException("Fence owner and epoch must be positive");
    }
    if (sequence < 1L) {
      throw new IllegalArgumentException("Snapshot sequence must be positive");
    }
  }

  @Override
  public void encode(@NotNull DataOutput output) throws IOException {
    byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
    if (jsonBytes.length > MAX_JSON_BYTES) {
      throw new IOException("Player data exceeds MEDIUMTEXT limit: " + jsonBytes.length + " bytes");
    }
    output.writeLong(uuid.getMostSignificantBits());
    output.writeLong(uuid.getLeastSignificantBits());
    output.writeLong(requestId.getMostSignificantBits());
    output.writeLong(requestId.getLeastSignificantBits());
    output.writeLong(ownerToken.getMostSignificantBits());
    output.writeLong(ownerToken.getLeastSignificantBits());
    output.writeLong(epoch);
    output.writeLong(sequence);
    output.writeInt(jsonBytes.length);
    output.write(jsonBytes);
  }

  @NotNull
  public static DataMessage decode(@NotNull DataInput input) throws IOException {
    UUID uuid = new UUID(input.readLong(), input.readLong());
    UUID requestId = new UUID(input.readLong(), input.readLong());
    UUID ownerToken = new UUID(input.readLong(), input.readLong());
    long epoch = input.readLong();
    long sequence = input.readLong();
    if (ZERO_OWNER_TOKEN.equals(ownerToken) || epoch < 1L) {
      throw new IOException("Invalid Redis player data fence epoch: " + epoch);
    }
    if (sequence < 1L) {
      throw new IOException("Invalid Redis player data sequence: " + sequence);
    }
    int jsonLength = input.readInt();
    if (jsonLength < 0 || jsonLength > MAX_JSON_BYTES) {
      throw new IOException("Invalid Redis player data length: " + jsonLength);
    }
    byte[] jsonBytes = new byte[jsonLength];
    input.readFully(jsonBytes);
    String json;
    try {
      json = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(jsonBytes))
          .toString();
    } catch (CharacterCodingException error) {
      throw new IOException("Invalid Redis player data UTF-8", error);
    }
    return new DataMessage(
        uuid,
        requestId,
        ownerToken,
        epoch,
        sequence,
        json
    );
  }
}
