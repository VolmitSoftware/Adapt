package com.volmit.adapt.util.redis.codec;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.ByteBuffer;

@AllArgsConstructor
public class ByteBufferInputStream extends InputStream {
    private final @NonNull ByteBuffer buffer;

    @Override
    public int read() {
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(byte @NotNull [] b) {
        buffer.get(b, 0, b.length);
        return b.length;
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) {
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
