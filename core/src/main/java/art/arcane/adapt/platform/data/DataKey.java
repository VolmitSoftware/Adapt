package art.arcane.adapt.platform.data;

import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/**
 * Typed persistent data key. The id becomes the Bukkit namespaced key or the vanilla tag key, so
 * keep ids identical to the ones the Bukkit build already writes (for example {@code adapt:elevator}).
 */
public record DataKey<T>(Id id, DataCodec<T> codec) {
  public DataKey {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(codec, "codec");
  }

  public static <T> DataKey<T> of(Id id, DataCodec<T> codec) {
    return new DataKey<>(id, codec);
  }

  public static <T> DataKey<T> adapt(String path, DataCodec<T> codec) {
    return new DataKey<>(Id.adapt(path), codec);
  }

  public DataType type() {
    return codec.type();
  }
}
