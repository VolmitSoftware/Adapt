package art.arcane.adapt.pack.raster;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/** An ARGB image with straight alpha, read and written as PNG. Pixels outside it read as clear and are not written. */
public final class Raster {
    private final int width;
    private final int height;
    private final int[] pixels;

    public Raster(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("raster size must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    public static Raster decode(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalArgumentException("not a PNG image");
            }
            Raster raster = new Raster(image.getWidth(), image.getHeight());
            image.getRGB(0, 0, raster.width, raster.height, raster.pixels, 0, raster.width);
            return raster;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int get(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : pixels[y * width + x];
    }

    public void set(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < width && y < height) {
            pixels[y * width + x] = argb;
        }
    }

    /** Composites {@code argb} over the pixel at (x, y) the way the client blends GUI quads. */
    public void blend(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int sa = argb >>> 24;
        if (sa == 0) {
            return;
        }
        int at = y * width + x;
        if (sa == 255) {
            pixels[at] = argb;
            return;
        }
        pixels[at] = over(argb, pixels[at]);
    }

    static int over(int source, int target) {
        int sa = source >>> 24;
        int da = target >>> 24;
        if (da == 255) {
            int r = (((source >> 16) & 0xFF) * sa + ((target >> 16) & 0xFF) * (255 - sa) + 127) / 255;
            int g = (((source >> 8) & 0xFF) * sa + ((target >> 8) & 0xFF) * (255 - sa) + 127) / 255;
            int b = ((source & 0xFF) * sa + (target & 0xFF) * (255 - sa) + 127) / 255;
            return 0xFF000000 | r << 16 | g << 8 | b;
        }
        int keep = da * (255 - sa);
        int outA255 = sa * 255 + keep;
        if (outA255 == 0) {
            return 0;
        }
        int r = (((source >> 16) & 0xFF) * sa * 255 + ((target >> 16) & 0xFF) * keep + outA255 / 2) / outA255;
        int g = (((source >> 8) & 0xFF) * sa * 255 + ((target >> 8) & 0xFF) * keep + outA255 / 2) / outA255;
        int b = ((source & 0xFF) * sa * 255 + (target & 0xFF) * keep + outA255 / 2) / outA255;
        int a = (outA255 + 127) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    public Raster copy() {
        Raster copy = new Raster(width, height);
        System.arraycopy(pixels, 0, copy.pixels, 0, pixels.length);
        return copy;
    }

    /** A copy with every pixel multiplied by the opaque RGB colour {@code rgb}, as the client tints a model layer. */
    public Raster multiplied(int rgb) {
        Raster tinted = new Raster(width, height);
        int colour = 0xFF000000 | rgb;
        for (int index = 0; index < pixels.length; index++) {
            tinted.pixels[index] = RasterDraw.multiply(pixels[index], colour);
        }
        return tinted;
    }

    /** The part from (x, y) of size width x height; outside parts read clear. */
    public Raster crop(int x, int y, int cropWidth, int cropHeight) {
        Raster part = new Raster(cropWidth, cropHeight);
        for (int row = 0; row < cropHeight; row++) {
            for (int column = 0; column < cropWidth; column++) {
                part.pixels[row * cropWidth + column] = get(x + column, y + row);
            }
        }
        return part;
    }

    /** Draws {@code other} with its top left at (x, y), composited over. */
    public void paste(Raster other, int x, int y) {
        for (int row = 0; row < other.height; row++) {
            for (int column = 0; column < other.width; column++) {
                blend(x + column, y + row, other.pixels[row * other.width + column]);
            }
        }
    }

    public void clear() {
        Arrays.fill(pixels, 0);
    }

    /** The pixels, row by row; the array is the raster's own. */
    public int[] pixels() {
        return pixels;
    }

    public boolean empty() {
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                return false;
            }
        }
        return true;
    }

    /** The smallest box holding every visible pixel, as {x, y, width, height}, or null when there is none. */
    public int[] bounds() {
        int x0 = width;
        int y0 = height;
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1 - x0 + 1, y1 - y0 + 1};
    }

    /** Whether the two rasters hold the same pixels. */
    public boolean same(Raster other) {
        return other.width == width && other.height == height && Arrays.equals(pixels, other.pixels);
    }

    public BufferedImage image() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /** The raster as a PNG; the same pixels always give the same bytes. */
    public byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image(), "png", out)) {
                throw new IllegalStateException("no PNG writer");
            }
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
