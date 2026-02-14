package art.arcane.adapt.util.project.redis.codec;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

public record DataMessage(
        @NonNull UUID uuid,
        @NonNull String json
) implements Message {

    @Override
    public void encode(@NotNull DataOutput output) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
        output.writeUTF(json);
    }

    @NotNull
    public static DataMessage decode(@NotNull DataInput input) throws IOException {
        return new DataMessage(
                new UUID(input.readLong(), input.readLong()),
                input.readUTF()
        );
    }
}
