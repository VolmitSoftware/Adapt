package art.arcane.adapt.platform.data;

import org.jetbrains.annotations.ApiStatus;

/**
 * Converts a typed value to and from one {@link DataType}. {@link #decode} returns null when the
 * stored value has a different type or cannot be read, which callers treat as absent.
 *
 * @param <T> the Java type the codec reads and writes
 */
@ApiStatus.AvailableSince("1.0.0")
public interface DataCodec<T> {
  /** The storage kind {@link #encode} produces. */
  DataType type();

  /** The stored form of a non-null value. */
  DataValue encode(T value);

  /** The value read from storage, or null when {@code value} is null, of another type, or unreadable. */
  T decode(DataValue value);
}
