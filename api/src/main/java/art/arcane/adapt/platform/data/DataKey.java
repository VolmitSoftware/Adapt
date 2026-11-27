package art.arcane.adapt.platform.data;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Typed persistent data key. The id becomes the Bukkit namespaced key or the vanilla tag key, so
 * keep ids identical to the ones the Bukkit build already writes (for example {@code adapt:elevator}).
 *
 * @param id the storage key
 * @param codec how values are written and read
 * @param <T> the value type
 */
@ApiStatus.AvailableSince("1.0.0")
public record DataKey<T>(Id id, DataCodec<T> codec) {
  public DataKey {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(codec, "codec");
  }

  /** Same as the constructor. */
  public static <T> DataKey<T> of(Id id, DataCodec<T> codec) {
    return new DataKey<>(id, codec);
  }

  /** A key in the {@code adapt} namespace. */
  public static <T> DataKey<T> adapt(String path, DataCodec<T> codec) {
    return new DataKey<>(Id.adapt(path), codec);
  }

  /** The storage kind of the codec. */
  public DataType type() {
    return codec.type();
  }
}
