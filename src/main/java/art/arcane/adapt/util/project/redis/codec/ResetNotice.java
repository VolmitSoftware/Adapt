package art.arcane.adapt.util.project.redis.codec;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public record ResetNotice(
    @NonNull UUID uuid,
    @NonNull UUID operationId,
    long epoch,
    boolean purge
) implements Message {
  public ResetNotice {
    Objects.requireNonNull(uuid);
    Objects.requireNonNull(operationId);
    if (epoch < 1L) {
      throw new IllegalArgumentException("Reset fence epoch must be positive");
    }
  }

  @Override
  public void encode(@NotNull DataOutput output) throws IOException {
    output.writeLong(uuid.getMostSignificantBits());
    output.writeLong(uuid.getLeastSignificantBits());
    output.writeLong(operationId.getMostSignificantBits());
    output.writeLong(operationId.getLeastSignificantBits());
    output.writeLong(epoch);
    output.writeBoolean(purge);
  }

  @NonNull
  public static ResetNotice decode(@NotNull DataInput input) throws IOException {
    UUID uuid = new UUID(input.readLong(), input.readLong());
    UUID operationId = new UUID(input.readLong(), input.readLong());
    long epoch = input.readLong();
    if (epoch < 1L) {
      throw new IOException("Invalid Redis reset fence epoch: " + epoch);
    }
    return new ResetNotice(uuid, operationId, epoch, input.readBoolean());
  }
}
