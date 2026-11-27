package art.arcane.adapt.engine.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Every addon slice value of one block: each slice id with the bytes its codec wrote, ordered by id. Values of slices
 * no installed addon declares are kept as they are.
 */
public final class AddonSliceValues {
  static final int MAX_ENTRIES = 1024;
  static final int MAX_VALUE_BYTES = 8 * 1024 * 1024;

  private final String[] ids;
  private final byte[][] values;

  private AddonSliceValues(String[] ids, byte[][] values) {
    this.ids = ids;
    this.values = values;
  }

  static AddonSliceValues of(String id, byte[] value) {
    return new AddonSliceValues(new String[]{id}, new byte[][]{value.clone()});
  }

  /** The stored bytes of slice {@code id}, or null. */
  byte[] get(String id) {
    int index = Arrays.binarySearch(ids, id);
    return index < 0 ? null : values[index].clone();
  }

  AddonSliceValues with(String id, byte[] value) {
    int index = Arrays.binarySearch(ids, id);
    if (index >= 0) {
      byte[][] replaced = values.clone();
      replaced[index] = value.clone();
      return new AddonSliceValues(ids, replaced);
    }
    int at = -index - 1;
    String[] grownIds = new String[ids.length + 1];
    byte[][] grownValues = new byte[ids.length + 1][];
    System.arraycopy(ids, 0, grownIds, 0, at);
    System.arraycopy(values, 0, grownValues, 0, at);
    grownIds[at] = id;
    grownValues[at] = value.clone();
    System.arraycopy(ids, at, grownIds, at + 1, ids.length - at);
    System.arraycopy(values, at, grownValues, at + 1, ids.length - at);
    return new AddonSliceValues(grownIds, grownValues);
  }

  /** These values without slice {@code id}; null when nothing is left. */
  AddonSliceValues without(String id) {
    int index = Arrays.binarySearch(ids, id);
    if (index < 0) {
      return this;
    }
    if (ids.length == 1) {
      return null;
    }
    String[] keptIds = new String[ids.length - 1];
    byte[][] keptValues = new byte[ids.length - 1][];
    System.arraycopy(ids, 0, keptIds, 0, index);
    System.arraycopy(values, 0, keptValues, 0, index);
    System.arraycopy(ids, index + 1, keptIds, index, ids.length - index - 1);
    System.arraycopy(values, index + 1, keptValues, index, ids.length - index - 1);
    return new AddonSliceValues(keptIds, keptValues);
  }

  void write(DataOutputStream out) throws IOException {
    out.writeShort(ids.length);
    for (int index = 0; index < ids.length; index++) {
      out.writeUTF(ids[index]);
      out.writeInt(values[index].length);
      out.write(values[index]);
    }
  }

  static AddonSliceValues read(DataInputStream in) throws IOException {
    int count = in.readUnsignedShort();
    if (count == 0 || count > MAX_ENTRIES) {
      throw new IOException("Addon slice block holds " + count + " values; 1 to " + MAX_ENTRIES + " are allowed");
    }
    String[] ids = new String[count];
    byte[][] values = new byte[count][];
    for (int index = 0; index < count; index++) {
      ids[index] = in.readUTF();
      if (index > 0 && ids[index - 1].compareTo(ids[index]) >= 0) {
        throw new IOException("Addon slice ids are out of order at " + ids[index]);
      }
      int length = in.readInt();
      if (length < 0 || length > MAX_VALUE_BYTES) {
        throw new IOException("Addon slice " + ids[index] + " claims " + length + " bytes");
      }
      values[index] = in.readNBytes(length);
      if (values[index].length != length) {
        throw new IOException("Addon slice " + ids[index] + " ends early");
      }
    }
    return new AddonSliceValues(ids, values);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AddonSliceValues that && Arrays.equals(ids, that.ids)
        && Arrays.deepEquals(values, that.values);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(ids) + Arrays.deepHashCode(values);
  }
}
