package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * One kind of value stored per block in the world's own block data, outside the block itself, read and written with
 * {@link BlockStore}. Only {@link art.arcane.adapt.api.addon.ContentRegistrar#blockSlice} makes one.
 *
 * <p>A slice is known by its id and the name of its value type. Each value is stored as the bytes its codec writes,
 * under the slice id, so any number of addons may declare any number of slices on every platform, and two slices
 * may share a value type. When an addon is installed again, for example after a reload, its new declaration replaces
 * the old one: a slice object from the earlier install keeps working and reads with the new codec, as long as the id
 * and the type name are the same. Values of a slice no installed addon declares stay in the world data untouched.
 *
 * @param <T> the stored value type
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface BlockSlice<T> {
  /** The slice id, {@code <namespace>:<path>} of the declaring addon; it is file content and must never change. */
  Id id();

  /** The class of the stored values. */
  Class<T> type();

  /** Writes and reads one value. */
  Codec<T> codec();

  /**
   * The byte form of one stored value. Whatever {@link #write} produces, {@link #read} must read back in full.
   *
   * @param <T> the stored value type
   */
  @ApiStatus.Experimental
  interface Codec<T> {
    /** Writes {@code value} to {@code out}. */
    void write(T value, DataOutputStream out) throws IOException;

    /** Reads one value written by {@link #write}. */
    T read(DataInputStream in) throws IOException;
  }
}
