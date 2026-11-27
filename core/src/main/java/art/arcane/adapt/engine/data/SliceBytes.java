package art.arcane.adapt.engine.data;

import art.arcane.adapt.api.world.BlockSlice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** One block slice value as the bytes its codec writes, the form every store keeps it in. */
public final class SliceBytes {
  private SliceBytes() {
  }

  public static <T> byte[] encode(BlockSlice<T> slice, T value) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      slice.codec().write(value, out);
    } catch (IOException failure) {
      throw new UncheckedIOException("Could not encode block slice " + slice.id(), failure);
    }
    return bytes.toByteArray();
  }

  public static <T> T decode(BlockSlice<T> slice, byte[] bytes) {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      return slice.codec().read(in);
    } catch (IOException failure) {
      throw new UncheckedIOException("Could not decode block slice " + slice.id(), failure);
    }
  }
}
