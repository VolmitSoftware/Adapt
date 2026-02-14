package art.arcane.adapt.util.project.redis.codec;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

public record DataRequest(
        @NonNull UUID uuid
) implements Message {
    @Override
    public void encode(@NotNull DataOutput output) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    @NonNull
    public static DataRequest decode(@NotNull DataInput input) throws IOException {
        return new DataRequest(new UUID(input.readLong(), input.readLong()));
    }
}
