package art.arcane.adapt.engine.asset;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Reads a PNG's size and its pixels and writes plain 8-bit RGBA PNGs, without AWT. */
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

  /**
   * The ARGB pixels of a non-interlaced PNG, row by row, or null when it is not one or uses 16-bit channels: grey, RGB,
   * palette (1 to 8 bits, with its transparency), grey with alpha and RGBA.
   */
  public static int[] decode(byte[] data) {
    int[] size = size(data);
    if (size == null) {
      return null;
    }
    int width = size[0];
    int height = size[1];
    int depth = data[24] & 0xFF;
    int type = data[25] & 0xFF;
    if (data[28] != 0 || (type != 3 && depth != 8) || (type == 3 && depth > 8)) {
      return null;
    }
    int channels = switch (type) {
      case 0, 3 -> 1;
      case 2 -> 3;
      case 4 -> 2;
      case 6 -> 4;
      default -> 0;
    };
    if (channels == 0) {
      return null;
    }
    int[] palette = new int[256];
    ByteArrayOutputStream packed = new ByteArrayOutputStream();
    int at = SIGNATURE.length;
    while (at + 8 <= data.length) {
      int length = int32(data, at);
      String name = new String(data, at + 4, 4, StandardCharsets.US_ASCII);
      int body = at + 8;
      if (length < 0 || body + length > data.length) {
        return null;
      }
      if (name.equals("PLTE")) {
        for (int entry = 0; entry < length / 3; entry++) {
          palette[entry] = 0xFF000000 | (data[body + entry * 3] & 0xFF) << 16 | (data[body + entry * 3 + 1] & 0xFF) << 8 | data[body + entry * 3 + 2] & 0xFF;
        }
      } else if (name.equals("tRNS") && type == 3) {
        for (int entry = 0; entry < length && entry < 256; entry++) {
          palette[entry] = (palette[entry] & 0xFFFFFF) | (data[body + entry] & 0xFF) << 24;
        }
      } else if (name.equals("IDAT")) {
        packed.write(data, body, length);
      }
      at = body + length + 4;
    }
    int stride = (width * channels * depth + 7) / 8;
    byte[] raw = new byte[height * (stride + 1)];
    Inflater inflater = new Inflater();
    try {
      inflater.setInput(packed.toByteArray());
      int read = 0;
      while (read < raw.length && !inflater.finished()) {
        int got = inflater.inflate(raw, read, raw.length - read);
        if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          return null;
        }
        read += got;
      }
      if (read < raw.length) {
        return null;
      }
    } catch (DataFormatException broken) {
      return null;
    } finally {
      inflater.end();
    }
    int pixelBytes = Math.max(1, channels * depth / 8);
    byte[] previous = new byte[stride];
    byte[] row = new byte[stride];
    int[] argb = new int[width * height];
    for (int y = 0; y < height; y++) {
      int filter = raw[y * (stride + 1)] & 0xFF;
      if (filter > 4) {
        return null;
      }
      for (int x = 0; x < stride; x++) {
        int value = raw[y * (stride + 1) + 1 + x] & 0xFF;
        int left = x >= pixelBytes ? row[x - pixelBytes] & 0xFF : 0;
        int up = previous[x] & 0xFF;
        int corner = x >= pixelBytes ? previous[x - pixelBytes] & 0xFF : 0;
        row[x] = (byte) switch (filter) {
          case 0 -> value;
          case 1 -> value + left;
          case 2 -> value + up;
          case 3 -> value + (left + up) / 2;
          default -> value + paeth(left, up, corner);
        };
      }
      for (int x = 0; x < width; x++) {
        argb[y * width + x] = switch (type) {
          case 0 -> 0xFF000000 | (row[x] & 0xFF) * 0x010101;
          case 2 -> 0xFF000000 | (row[x * 3] & 0xFF) << 16 | (row[x * 3 + 1] & 0xFF) << 8 | row[x * 3 + 2] & 0xFF;
          case 3 -> palette[(row[x * depth / 8] & 0xFF) >> (8 - depth - x * depth % 8) & (1 << depth) - 1];
          case 4 -> (row[x * 2 + 1] & 0xFF) << 24 | (row[x * 2] & 0xFF) * 0x010101;
          default -> (row[x * 4 + 3] & 0xFF) << 24 | (row[x * 4] & 0xFF) << 16 | (row[x * 4 + 1] & 0xFF) << 8 | row[x * 4 + 2] & 0xFF;
        };
      }
      byte[] swap = previous;
      previous = row;
      row = swap;
    }
    return argb;
  }

  private static int paeth(int left, int up, int corner) {
    int estimate = left + up - corner;
    int toLeft = Math.abs(estimate - left);
    int toUp = Math.abs(estimate - up);
    int toCorner = Math.abs(estimate - corner);
    return toLeft <= toUp && toLeft <= toCorner ? left : toUp <= toCorner ? up : corner;
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
