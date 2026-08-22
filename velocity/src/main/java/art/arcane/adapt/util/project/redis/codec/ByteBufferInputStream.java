package art.arcane.adapt.util.project.redis.codec;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

@AllArgsConstructor
public class ByteBufferInputStream extends InputStream {
    private final @NonNull ByteBuffer buffer;

    @Override
    public int read() throws IOException {
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(byte @NotNull [] b) throws IOException {
        buffer.get(b, 0, b.length);
        return b.length;
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException {
        buffer.get(b, off, len);
        return len;
    }

    @Override
    public byte @NotNull [] readNBytes(int len) {
        byte[] b = new byte[len];
        buffer.get(b, 0, len);
        return b;
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) {
        buffer.get(b, off, len);
        return len;
    }
}
