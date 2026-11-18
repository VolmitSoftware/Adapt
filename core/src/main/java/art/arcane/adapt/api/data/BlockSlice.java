package art.arcane.adapt.api.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * One kind of value stored per block in the world store. {@code id} is the stable slice name written into the
 * files (never change it once released); {@code codec} writes and reads one value.
 */
public record BlockSlice<T>(String id, Class<T> type, Codec<T> codec) {
  public BlockSlice {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(codec, "codec");
  }

  public interface Codec<T> {
    void write(T value, DataOutputStream out) throws IOException;

    T read(DataInputStream in) throws IOException;
  }
}
