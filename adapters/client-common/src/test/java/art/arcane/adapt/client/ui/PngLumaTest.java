package art.arcane.adapt.client.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** PNG luminance against the JDK's own decoder, for every colour type game textures use. */
class PngLumaTest {
    private static final double TOLERANCE = 0.002;

    @Test
    void meanLuminanceMatchesTheDecodedImageForEveryColourType() throws IOException {
        int[] gradient = new int[16 * 16];
        for (int index = 0; index < gradient.length; index++) {
            int x = index % 16;
            int y = index / 16;
            int alpha = x == 0 ? 0 : 0xFF;
            gradient[index] = alpha << 24 | (x * 16) << 16 | (y * 16) << 8 | (x + y) * 7;
        }
        assertMatches(image(BufferedImage.TYPE_INT_ARGB, gradient));
        assertMatches(image(BufferedImage.TYPE_INT_RGB, gradient));
        assertMatches(image(BufferedImage.TYPE_BYTE_GRAY, gradient));
        byte[] red = {0, (byte) 200, 40, (byte) 255};
        byte[] green = {0, 30, (byte) 220, (byte) 255};
        byte[] blue = {0, 90, 10, (byte) 255};
        byte[] alpha = {0, (byte) 255, (byte) 255, (byte) 255};
        for (int bits : new int[] {2, 4, 8}) {
            IndexColorModel palette = new IndexColorModel(bits, 4, red, green, blue, alpha);
            BufferedImage indexed = bits == 8 ? new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_INDEXED, palette)
                    : new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_BINARY, palette);
            for (int index = 0; index < 256; index++) {
                indexed.getRaster().setSample(index % 16, index / 16, 0, (index * 7 + index / 16) % 4);
            }
            assertMatches(indexed);
        }
    }

    @Test
    void onlyTheFirstSquareFrameOfAnAnimationStripCounts() throws IOException {
        BufferedImage strip = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 16; x++) {
                strip.setRGB(x, y, y < 16 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        assertEquals(0.0F, PngLuma.mean(encode(strip)), TOLERANCE);
    }

    @Test
    void unreadableOrEmptyImagesAreUnknown() throws IOException {
        assertEquals(PngLuma.UNKNOWN, PngLuma.mean(new byte[] {1, 2, 3}));
        assertEquals(PngLuma.UNKNOWN, PngLuma.mean(FakeResources.png(16, 16)));
        assertEquals(PngLuma.UNKNOWN, PngLuma.mean(encode(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))));
    }

    @Test
    void theDarkVanillaIconsMeasureDark() throws IOException {
        byte[] rose = vanilla("assets/minecraft/textures/block/wither_rose.png");
        byte[] pickaxe = vanilla("assets/minecraft/textures/item/netherite_pickaxe.png");
        byte[] star = vanilla("assets/minecraft/textures/item/nether_star.png");
        assumeTrue(rose != null && pickaxe != null && star != null, "vanilla textures are not on this test classpath");
        assertMatches(ImageIO.read(new java.io.ByteArrayInputStream(rose)));
        assertTrue(PngLuma.mean(rose) < 0.05F, "wither rose " + PngLuma.mean(rose));
        assertTrue(PngLuma.mean(pickaxe) < 0.06F, "netherite pickaxe " + PngLuma.mean(pickaxe));
        assertTrue(PngLuma.mean(star) > 0.3F, "nether star " + PngLuma.mean(star));
    }

    private static void assertMatches(BufferedImage image) throws IOException {
        double sum = 0.0;
        int count = 0;
        int side = Math.min(image.getWidth(), image.getHeight());
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = argb(image, x, y);
                if (argb >>> 24 >= 128) {
                    sum += PngLuma.luminance(argb);
                    count++;
                }
            }
        }
        assertEquals(sum / count, PngLuma.mean(encode(image)), TOLERANCE, "image type " + image.getType());
    }

    /** The pixel as stored; the JDK's grey images report a gamma-converted colour instead of the stored sample. */
    private static int argb(BufferedImage image, int x, int y) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            int gray = image.getRaster().getSample(x, y, 0);
            return 0xFF000000 | gray << 16 | gray << 8 | gray;
        }
        return image.getRGB(x, y);
    }

    private static BufferedImage image(int type, int[] argb) {
        BufferedImage image = new BufferedImage(16, 16, type);
        image.setRGB(0, 0, 16, 16, argb, 0, 16);
        return image;
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] vanilla(String path) throws IOException {
        try (InputStream stream = PngLumaTest.class.getClassLoader().getResourceAsStream(path)) {
            return stream == null ? null : stream.readAllBytes();
        }
    }
}
