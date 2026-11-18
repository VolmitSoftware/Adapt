package art.arcane.adapt.platform.data;

/**
 * Converts a typed value to and from one {@link DataType}. {@link #decode} returns null when the
 * stored value has a different type or cannot be read, which callers treat as absent.
 */
public interface DataCodec<T> {
  DataType type();

  DataValue encode(T value);

  T decode(DataValue value);
}
