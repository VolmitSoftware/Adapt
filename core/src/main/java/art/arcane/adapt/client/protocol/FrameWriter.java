package art.arcane.adapt.client.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/** Big-endian writer for frames. Every violated bound throws {@link IllegalArgumentException}. */
final class FrameWriter {
  private byte[] buffer;
  private int size;

  FrameWriter(int capacity) {
    buffer = new byte[Math.max(16, capacity)];
  }

  void u8(int value) {
    if (value < 0 || value > 0xFF) {
      throw new IllegalArgumentException("byte out of range: " + value);
    }
    ensure(1);
    buffer[size++] = (byte) value;
  }

  void bool(boolean value) {
    u8(value ? 1 : 0);
  }

  void varInt(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("negative value: " + value);
    }
    varLong(value);
  }

  void varLong(long value) {
    if (value < 0L) {
      throw new IllegalArgumentException("negative value: " + value);
    }
    long remaining = value;
    while ((remaining & ~0x7FL) != 0L) {
      u8((int) ((remaining & 0x7FL) | 0x80L));
      remaining >>>= 7;
    }
    u8((int) remaining);
  }

  void i32(int value) {
    ensure(4);
    buffer[size++] = (byte) (value >>> 24);
    buffer[size++] = (byte) (value >>> 16);
    buffer[size++] = (byte) (value >>> 8);
    buffer[size++] = (byte) value;
  }

  void i64(long value) {
    i32((int) (value >>> 32));
    i32((int) value);
  }

  void f64(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("non-finite number: " + value);
    }
    i64(Double.doubleToLongBits(value));
  }

  void string(String value, int maxBytes) {
    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
    if (encoded.length > maxBytes) {
      throw new IllegalArgumentException("string of " + encoded.length + " bytes exceeds " + maxBytes);
    }
    varInt(encoded.length);
    raw(encoded);
  }

  void optionalString(String value, int maxBytes) {
    bool(value != null);
    if (value != null) {
      string(value, maxBytes);
    }
  }

  void bytes(byte[] value, int maxBytes) {
    if (value.length > maxBytes) {
      throw new IllegalArgumentException("payload of " + value.length + " bytes exceeds " + maxBytes);
    }
    varInt(value.length);
    raw(value);
  }

  /** Writes the constant's name, so constants added or reordered later never change what an older reader sees. */
  <E extends Enum<E>> void enumValue(E value) {
    string(value.name(), FrameLimits.MAX_ID_BYTES);
  }

  <T> void list(List<T> values, int maxCount, BiConsumer<FrameWriter, T> element) {
    if (values.size() > maxCount) {
      throw new IllegalArgumentException("list of " + values.size() + " exceeds " + maxCount);
    }
    varInt(values.size());
    for (T value : values) {
      element.accept(this, value);
    }
  }

  int size() {
    return size;
  }

  byte[] toByteArray() {
    return Arrays.copyOf(buffer, size);
  }

  private void raw(byte[] value) {
    ensure(value.length);
    System.arraycopy(value, 0, buffer, size, value.length);
    size += value.length;
  }

  private void ensure(int extra) {
    if (size + extra > buffer.length) {
      buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + extra));
    }
  }
}
