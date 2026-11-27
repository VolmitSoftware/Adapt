package art.arcane.adapt.engine.data;

import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.engine.data.BlockStorePort.Visitor;

/**
 * Block slice values in a world's mantle: a slice with a mantle slice of its own is read and written by its value
 * type, every other slice as its bytes in the block's {@link AddonSliceValues}.
 */
public final class MantleSlices {
  private static final Object WRITES = new Object();

  private MantleSlices() {
  }

  public static <T> T get(WorldData data, int x, int y, int z, BlockSlice<T> slice) {
    if (AdaptSlices.dedicated(slice)) {
      return data.get(x, y, z, slice.type());
    }
    AddonSliceValues values = data.get(x, y, z, AddonSliceValues.class);
    byte[] bytes = values == null ? null : values.get(slice.id().toString());
    return bytes == null ? null : SliceBytes.decode(slice, bytes);
  }

  public static <T> void set(WorldData data, int x, int y, int z, BlockSlice<T> slice, T value) {
    if (value == null) {
      remove(data, x, y, z, slice);
      return;
    }
    if (AdaptSlices.dedicated(slice)) {
      data.set(x, y, z, value);
      return;
    }
    byte[] bytes = SliceBytes.encode(slice, value);
    String id = slice.id().toString();
    synchronized (WRITES) {
      AddonSliceValues values = data.get(x, y, z, AddonSliceValues.class);
      data.set(x, y, z, values == null ? AddonSliceValues.of(id, bytes) : values.with(id, bytes));
    }
  }

  public static <T> void remove(WorldData data, int x, int y, int z, BlockSlice<T> slice) {
    if (AdaptSlices.dedicated(slice)) {
      data.remove(x, y, z, slice.type());
      return;
    }
    synchronized (WRITES) {
      AddonSliceValues values = data.get(x, y, z, AddonSliceValues.class);
      if (values == null) {
        return;
      }
      AddonSliceValues left = values.without(slice.id().toString());
      if (left == null) {
        data.remove(x, y, z, AddonSliceValues.class);
      } else if (left != values) {
        data.set(x, y, z, left);
      }
    }
  }

  public static <T> void iterateChunk(WorldData data, int chunkX, int chunkZ, BlockSlice<T> slice, Visitor<T> visitor) {
    if (AdaptSlices.dedicated(slice)) {
      data.iterateChunk(chunkX, chunkZ, slice.type(), visitor::accept);
      return;
    }
    String id = slice.id().toString();
    data.iterateChunk(chunkX, chunkZ, AddonSliceValues.class, (Integer x, Integer y, Integer z, AddonSliceValues values) -> {
      byte[] bytes = values.get(id);
      if (bytes != null) {
        visitor.accept(x, y, z, SliceBytes.decode(slice, bytes));
      }
    });
  }
}
