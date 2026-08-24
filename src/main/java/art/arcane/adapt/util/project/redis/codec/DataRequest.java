package art.arcane.adapt.util.project.redis.codec;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public record DataRequest(
    @NonNull UUID uuid,
    @NonNull UUID requestId,
    @NonNull UUID ownerToken,
    long epoch
) implements Message {
  private static final UUID ZERO_OWNER_TOKEN = new UUID(0L, 0L);

  public DataRequest {
    Objects.requireNonNull(uuid);
    Objects.requireNonNull(requestId);
    Objects.requireNonNull(ownerToken);
    if (ZERO_OWNER_TOKEN.equals(ownerToken) || epoch < 1L) {
      throw new IllegalArgumentException("Requested fence owner and epoch must be positive");
    }
  }

  @Override
  public void encode(@NotNull DataOutput output) throws IOException {
    output.writeLong(uuid.getMostSignificantBits());
    output.writeLong(uuid.getLeastSignificantBits());
    output.writeLong(requestId.getMostSignificantBits());
    output.writeLong(requestId.getLeastSignificantBits());
    output.writeLong(ownerToken.getMostSignificantBits());
    output.writeLong(ownerToken.getLeastSignificantBits());
    output.writeLong(epoch);
  }

  @NonNull
  public static DataRequest decode(@NotNull DataInput input) throws IOException {
    UUID uuid = new UUID(input.readLong(), input.readLong());
    UUID requestId = new UUID(input.readLong(), input.readLong());
    UUID ownerToken = new UUID(input.readLong(), input.readLong());
    long epoch = input.readLong();
    if (ZERO_OWNER_TOKEN.equals(ownerToken) || epoch < 1L) {
      throw new IOException("Invalid requested Redis fence epoch: " + epoch);
    }
    return new DataRequest(uuid, requestId, ownerToken, epoch);
  }
}
