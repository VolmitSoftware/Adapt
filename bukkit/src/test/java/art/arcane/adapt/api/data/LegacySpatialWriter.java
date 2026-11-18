package art.arcane.adapt.api.data;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes sections and regions in the framing Spatial used, so tests can produce old-format worlds of any shape:
 * each slice is named by its value class and has no length prefix. The golden files under spatial/world come
 * from Spatial itself; this writer is for cases those files cannot hold, such as an item that stops loading.
 */
final class LegacySpatialWriter {
  private static final MantleDataAdapter<Matter> ADAPTER = new MantleDataAdapter<>() {
    @Override
    public Matter createSection() {
      return new IrisMatter(16, 16, 16);
    }

    @Override
    public Matter readSection(CountingDataInputStream din) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeSection(Matter section, DataOutputStream dos) throws IOException {
      write(section, dos);
    }

    @Override
    public void trimSection(Matter section) {
    }

    @Override
    public boolean isSectionEmpty(Matter section) {
      return section.getSliceMap().isEmpty();
    }

    @Override
    public Class<?> classifyValue(Object value) {
      return value.getClass();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(Matter section, int x, int y, int z, Class<?> type, T value) {
      MatterSlice<T> slice = (MatterSlice<T>) section.slice(type);
      slice.set(x, y, z, value);
    }

    @Override
    public <T> void remove(Matter section, int x, int y, int z, Class<T> type) {
      MatterSlice<T> slice = section.slice(type);
      slice.set(x, y, z, null);
    }

    @Override
    public <T> T get(Matter section, int x, int y, int z, Class<T> type) {
      MatterSlice<T> slice = section.slice(type);
      return slice.get(x, y, z);
    }

    @Override
    public <T> void iterate(Matter section, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasSlice(Matter section, Class<?> type) {
      return section.hasSlice(type);
    }

    @Override
    public void deleteSlice(Matter section, Class<?> type) {
      section.deleteSlice(type);
    }
  };

  private LegacySpatialWriter() {
  }

  static byte[] section(Matter section) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      write(section, out);
    }
    return bytes.toByteArray();
  }

  /**
   * Each slice as Spatial framed it: the value class name, then the palette body, with no length prefix.
   */
  static List<byte[]> slices(Matter section) throws IOException {
    List<byte[]> frames = new ArrayList<>();
    for (Class<?> type : section.getSliceTypes()) {
      ByteArrayOutputStream current = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(current)) {
        section.getSlice(type).write(out);
      }
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(current.toByteArray()));
      in.readUTF();
      ByteArrayOutputStream frame = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(frame)) {
        out.writeUTF(type.getCanonicalName());
        out.write(in.readAllBytes());
      }
      frames.add(frame.toByteArray());
    }
    return frames;
  }

  static byte[] prefix(Matter section) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(section.getWidth());
      out.writeInt(section.getHeight());
      out.writeInt(section.getDepth());
      out.writeByte(section.getSliceTypes().size());
      section.getHeader().write(out);
    }
    return bytes.toByteArray();
  }

  /**
   * One region file holding the given cells, which must all lie inside that region.
   */
  static byte[] region(SpatialWorldFixture.Region region,
                       int minHeight,
                       int maxHeight,
                       Map<SpatialWorldFixture.Cell, Object> cells) throws IOException {
    TectonicPlate<Matter> plate = new TectonicPlate<>(maxHeight - minHeight, region.x(), region.z(), ADAPTER, MantleHooks.NONE);
    for (Map.Entry<SpatialWorldFixture.Cell, Object> entry : cells.entrySet()) {
      SpatialWorldFixture.Cell cell = entry.getKey();
      if (!region.holds(cell.x(), cell.z())) {
        throw new IllegalArgumentException(cell + " is outside " + region);
      }
      MantleChunk<Matter> chunk = plate.getOrCreate((cell.x() >> 4) & 31, (cell.z() >> 4) & 31);
      int y = cell.y() - minHeight;
      Matter section = chunk.getOrCreate(y >> 4);
      ADAPTER.set(section, cell.x() & 15, y & 15, cell.z() & 15, cell.type(), entry.getValue());
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      plate.write(out);
    }
    return bytes.toByteArray();
  }

  private static void write(Matter section, DataOutputStream out) throws IOException {
    out.write(prefix(section));
    for (byte[] frame : slices(section)) {
      out.write(frame);
    }
  }
}
