package art.arcane.adapt.util.project.redis.codec;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

@AllArgsConstructor
public class ByteBufferInputStream extends InputStream {
  private final @NonNull ByteBuffer buffer;

  @Override
  public int read() {
    return buffer.hasRemaining() ? buffer.get() & 0xFF : -1;
  }

  @Override
  public int read(byte @NotNull [] bytes) {
    return read(bytes, 0, bytes.length);
  }

  @Override
  public int read(byte @NotNull [] bytes, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    if (length == 0) {
      return 0;
    }
    if (!buffer.hasRemaining()) {
      return -1;
    }
    int count = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, count);
    return count;
  }

  @Override
  public byte @NotNull [] readNBytes(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("Length must be nonnegative");
    }
    int count = Math.min(length, buffer.remaining());
    byte[] bytes = new byte[count];
    buffer.get(bytes);
    return bytes;
  }

  @Override
  public int readNBytes(byte[] bytes, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    int count = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, count);
    return count;
  }
}
