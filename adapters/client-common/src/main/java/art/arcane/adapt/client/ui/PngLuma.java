package art.arcane.adapt.client.ui;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The mean relative luminance (WCAG, 0 black to 1 white) of the opaque pixels of a PNG texture's first square frame,
 * so a tile can tell a dark icon from a light one. Reads 8-bit and lower depths of every colour type, non-interlaced,
 * which is how game and resource pack textures are stored; anything else answers {@link #UNKNOWN}.
 */
public final class PngLuma {
    public static final float UNKNOWN = -1.0F;
    public static final int MAX_BYTES = 1 << 20;
    private static final int MAX_SIDE = 512;
    private static final int OPAQUE = 128;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final double[] LINEAR = new double[256];

    static {
        for (int value = 0; value < 256; value++) {
            double channel = value / 255.0;
            LINEAR[value] = channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
    }

    private PngLuma() {
    }

    /** Relative luminance of an sRGB colour (alpha ignored). */
    public static double luminance(int rgb) {
        return 0.2126 * LINEAR[rgb >> 16 & 0xFF] + 0.7152 * LINEAR[rgb >> 8 & 0xFF] + 0.0722 * LINEAR[rgb & 0xFF];
    }

    /** The mean luminance of the image's opaque pixels, or {@link #UNKNOWN} when it is not a readable PNG or has none. */
    public static float mean(byte[] png) {
        try {
            return decode(png);
        } catch (DataFormatException | RuntimeException error) {
            return UNKNOWN;
        }
    }

    private static float decode(byte[] png) throws DataFormatException {
        for (int index = 0; index < SIGNATURE.length; index++) {
            if (png.length < SIGNATURE.length || png[index] != SIGNATURE[index]) {
                return UNKNOWN;
            }
        }
        int width = 0;
        int height = 0;
        int depth = 0;
        int type = -1;
        byte[] palette = new byte[0];
        byte[] transparency = new byte[0];
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int at = SIGNATURE.length;
        while (at + 8 <= png.length) {
            int length = readInt(png, at);
            String chunk = new String(png, at + 4, 4, StandardCharsets.US_ASCII);
            int body = at + 8;
            if (length < 0 || body + length > png.length) {
                return UNKNOWN;
            }
            switch (chunk) {
                case "IHDR" -> {
                    width = readInt(png, body);
                    height = readInt(png, body + 4);
                    depth = png[body + 8] & 0xFF;
                    type = png[body + 9] & 0xFF;
                    if (png[body + 12] != 0) {
                        return UNKNOWN;
                    }
                }
                case "PLTE" -> palette = slice(png, body, length);
                case "tRNS" -> transparency = slice(png, body, length);
                case "IDAT" -> data.write(png, body, length);
                default -> {
                }
            }
            if (chunk.equals("IEND")) {
                break;
            }
            at = body + length + 4;
        }
        int channels = switch (type) {
            case 0, 3 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> 0;
        };
        if (channels == 0 || depth > 8 || width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE * 64) {
            return UNKNOWN;
        }
        int rows = Math.min(width, height);
        int bitsPerPixel = channels * depth;
        int stride = (width * bitsPerPixel + 7) / 8;
        int step = Math.max(1, bitsPerPixel / 8);
        byte[] raw = inflate(data.toByteArray(), (stride + 1) * rows);
        if (raw.length < (stride + 1) * rows) {
            return UNKNOWN;
        }
        byte[] previous = new byte[stride];
        byte[] row = new byte[stride];
        double sum = 0.0;
        int counted = 0;
        for (int y = 0; y < rows; y++) {
            int filter = raw[y * (stride + 1)] & 0xFF;
            System.arraycopy(raw, y * (stride + 1) + 1, row, 0, stride);
            unfilter(filter, row, previous, step);
            for (int x = 0; x < width; x++) {
                int argb = pixel(row, x, type, depth, palette, transparency);
                if (argb >>> 24 >= OPAQUE) {
                    sum += luminance(argb);
                    counted++;
                }
            }
            byte[] swap = previous;
            previous = row;
            row = swap;
        }
        return counted == 0 ? UNKNOWN : (float) (sum / counted);
    }

    private static int pixel(byte[] row, int x, int type, int depth, byte[] palette, byte[] transparency) {
        return switch (type) {
            case 0 -> {
                int value = sample(row, x, depth);
                int gray = value * 255 / ((1 << depth) - 1);
                boolean clear = transparency.length >= 2 && ((transparency[0] & 0xFF) << 8 | transparency[1] & 0xFF) == value;
                yield (clear ? 0 : 0xFF000000) | gray << 16 | gray << 8 | gray;
            }
            case 2 -> {
                int red = row[x * 3] & 0xFF;
                int green = row[x * 3 + 1] & 0xFF;
                int blue = row[x * 3 + 2] & 0xFF;
                boolean clear = transparency.length >= 6 && (transparency[1] & 0xFF) == red && (transparency[3] & 0xFF) == green
                        && (transparency[5] & 0xFF) == blue;
                yield (clear ? 0 : 0xFF000000) | red << 16 | green << 8 | blue;
            }
            case 3 -> {
                int index = sample(row, x, depth);
                if (index * 3 + 2 >= palette.length) {
                    yield 0;
                }
                int alpha = index < transparency.length ? transparency[index] & 0xFF : 0xFF;
                yield alpha << 24 | (palette[index * 3] & 0xFF) << 16 | (palette[index * 3 + 1] & 0xFF) << 8 | palette[index * 3 + 2] & 0xFF;
            }
            case 4 -> {
                int gray = row[x * 2] & 0xFF;
                yield (row[x * 2 + 1] & 0xFF) << 24 | gray << 16 | gray << 8 | gray;
            }
            default -> (row[x * 4 + 3] & 0xFF) << 24 | (row[x * 4] & 0xFF) << 16 | (row[x * 4 + 1] & 0xFF) << 8 | row[x * 4 + 2] & 0xFF;
        };
    }

    private static int sample(byte[] row, int x, int depth) {
        if (depth == 8) {
            return row[x] & 0xFF;
        }
        int bit = x * depth;
        int shift = 8 - depth - bit % 8;
        return (row[bit / 8] & 0xFF) >> shift & (1 << depth) - 1;
    }

    private static void unfilter(int filter, byte[] row, byte[] previous, int step) {
        for (int index = 0; index < row.length; index++) {
            int left = index >= step ? row[index - step] & 0xFF : 0;
            int up = previous[index] & 0xFF;
            int corner = index >= step ? previous[index - step] & 0xFF : 0;
            int add = switch (filter) {
                case 1 -> left;
                case 2 -> up;
                case 3 -> (left + up) / 2;
                case 4 -> paeth(left, up, corner);
                default -> 0;
            };
            row[index] = (byte) (row[index] + add);
        }
    }

    private static int paeth(int left, int up, int corner) {
        int estimate = left + up - corner;
        int toLeft = Math.abs(estimate - left);
        int toUp = Math.abs(estimate - up);
        int toCorner = Math.abs(estimate - corner);
        if (toLeft <= toUp && toLeft <= toCorner) {
            return left;
        }
        return toUp <= toCorner ? up : corner;
    }

    private static byte[] inflate(byte[] compressed, int wanted) throws DataFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] out = new byte[wanted];
            int filled = 0;
            while (filled < wanted && !inflater.finished()) {
                int read = inflater.inflate(out, filled, wanted - filled);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                filled += read;
            }
            return filled == wanted ? out : new byte[0];
        } finally {
            inflater.end();
        }
    }

    private static int readInt(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8 | bytes[at + 3] & 0xFF;
    }

    private static byte[] slice(byte[] bytes, int from, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(bytes, from, copy, 0, length);
        return copy;
    }
}
