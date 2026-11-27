package art.arcane.adapt.client.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reader matching {@link FrameWriter}. Every malformed or out-of-bounds read throws {@link IllegalArgumentException}. */
final class FrameReader {
  private final byte[] data;
  private int position;

  FrameReader(byte[] data) {
    this.data = data;
  }

  int u8() {
    require(1);
    return data[position++] & 0xFF;
  }

  boolean bool() {
    int value = u8();
    if (value > 1) {
      throw new IllegalArgumentException("invalid boolean " + value);
    }
    return value == 1;
  }

  int varInt() {
    long value = varLong();
    if (value > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("varint out of range: " + value);
    }
    return (int) value;
  }

  long varLong() {
    long value = 0L;
    for (int shift = 0; shift < 63; shift += 7) {
      int next = u8();
      value |= (long) (next & 0x7F) << shift;
      if ((next & 0x80) == 0) {
        if (shift > 0 && next == 0) {
          throw new IllegalArgumentException("overlong varint");
        }
        return value;
      }
    }
    throw new IllegalArgumentException("varint too long");
  }

  int i32() {
    require(4);
    int value = ((data[position] & 0xFF) << 24) | ((data[position + 1] & 0xFF) << 16)
        | ((data[position + 2] & 0xFF) << 8) | (data[position + 3] & 0xFF);
    position += 4;
    return value;
  }

  long i64() {
    long high = i32() & 0xFFFFFFFFL;
    long low = i32() & 0xFFFFFFFFL;
    return (high << 32) | low;
  }

  double f64() {
    double value = Double.longBitsToDouble(i64());
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("non-finite number");
    }
    return value;
  }

  String string(int maxBytes) {
    int length = length(maxBytes);
    try {
      String value = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(data, position, length))
          .toString();
      position += length;
      return value;
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("invalid UTF-8 string", exception);
    }
  }

  String optionalString(int maxBytes) {
    return bool() ? string(maxBytes) : null;
  }

  byte[] bytes(int maxBytes) {
    int length = length(maxBytes);
    byte[] value = Arrays.copyOfRange(data, position, position + length);
    position += length;
    return value;
  }

  /** Reads a constant by name; a name this side does not know rejects the frame. */
  <E extends Enum<E>> E enumValue(Class<E> type) {
    String name = string(FrameLimits.MAX_ID_BYTES);
    E value = constant(type, name);
    if (value == null) {
      throw new IllegalArgumentException("unknown " + type.getSimpleName() + " " + name);
    }
    return value;
  }

  /** Reads a constant by name; a name this side does not know, as from a newer peer, reads as {@code fallback}. */
  <E extends Enum<E>> E enumValue(Class<E> type, E fallback) {
    E value = constant(type, string(FrameLimits.MAX_ID_BYTES));
    return value == null ? fallback : value;
  }

  private static <E extends Enum<E>> E constant(Class<E> type, String name) {
    for (E value : type.getEnumConstants()) {
      if (value.name().equals(name)) {
        return value;
      }
    }
    return null;
  }

  <T> List<T> list(int maxCount, Element<T> element) {
    int count = varInt();
    if (count > maxCount || count > remaining()) {
      throw new IllegalArgumentException("list of " + count + " exceeds its bound");
    }
    List<T> values = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      values.add(element.read(this));
    }
    return values;
  }

  void expectEnd() {
    if (position != data.length) {
      throw new IllegalArgumentException((data.length - position) + " trailing bytes");
    }
  }

  private int length(int maxBytes) {
    int length = varInt();
    if (length > maxBytes) {
      throw new IllegalArgumentException("length " + length + " exceeds " + maxBytes);
    }
    require(length);
    return length;
  }

  private int remaining() {
    return data.length - position;
  }

  private void require(int count) {
    if (count > remaining()) {
      throw new IllegalArgumentException("frame ends early");
    }
  }

  @FunctionalInterface
  interface Element<T> {
    T read(FrameReader in);
  }
}
