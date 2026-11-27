package art.arcane.adapt.engine.asset;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Reads a PNG's size from its header and writes plain 8-bit RGBA PNGs, without AWT. */
public final class PngImages {
  private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final int HEADER_BYTES = 24;

  private PngImages() {
  }

  /** Width and height from the IHDR chunk, or null when {@code data} does not start like a PNG. */
  public static int[] size(byte[] data) {
    if (data == null || data.length < HEADER_BYTES) {
      return null;
    }
    for (int index = 0; index < SIGNATURE.length; index++) {
      if (data[index] != SIGNATURE[index]) {
        return null;
      }
    }
    if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
      return null;
    }
    int width = int32(data, 16);
    int height = int32(data, 20);
    return width <= 0 || height <= 0 ? null : new int[]{width, height};
  }

  /** An RGBA PNG of {@code argb}, row by row; the same pixels always give the same bytes. */
  public static byte[] encode(int width, int height, int[] argb) {
    if (argb.length != width * height) {
      throw new IllegalArgumentException("expected " + width * height + " pixels, got " + argb.length);
    }
    byte[] raw = new byte[height * (width * 4 + 1)];
    int at = 0;
    for (int y = 0; y < height; y++) {
      raw[at++] = 0;
      for (int x = 0; x < width; x++) {
        int pixel = argb[y * width + x];
        raw[at++] = (byte) (pixel >>> 16);
        raw[at++] = (byte) (pixel >>> 8);
        raw[at++] = (byte) pixel;
        raw[at++] = (byte) (pixel >>> 24);
      }
    }
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
    deflater.setInput(raw);
    deflater.finish();
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    while (!deflater.finished()) {
      compressed.write(buffer, 0, deflater.deflate(buffer));
    }
    deflater.end();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(SIGNATURE);
    byte[] header = new byte[13];
    putInt32(header, 0, width);
    putInt32(header, 4, height);
    header[8] = 8;
    header[9] = 6;
    chunk(out, "IHDR", header);
    chunk(out, "IDAT", compressed.toByteArray());
    chunk(out, "IEND", new byte[0]);
    return out.toByteArray();
  }

  private static void chunk(ByteArrayOutputStream out, String type, byte[] body) {
    byte[] length = new byte[4];
    putInt32(length, 0, body.length);
    out.writeBytes(length);
    byte[] name = type.getBytes(StandardCharsets.US_ASCII);
    out.writeBytes(name);
    out.writeBytes(body);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(body);
    byte[] check = new byte[4];
    putInt32(check, 0, (int) crc.getValue());
    out.writeBytes(check);
  }

  private static int int32(byte[] data, int at) {
    return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
  }

  private static void putInt32(byte[] data, int at, int value) {
    data[at] = (byte) (value >>> 24);
    data[at + 1] = (byte) (value >>> 16);
    data[at + 2] = (byte) (value >>> 8);
    data[at + 3] = (byte) value;
  }
}
