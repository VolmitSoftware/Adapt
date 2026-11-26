package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiTexture;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A themed book and its hover frames draw without allocating per frame (under a byte a frame, which the JIT and the
 * allocation counter itself account for), at a bounded number of quads a frame.
 */
class ThemedBookCostTest {
    private static final int FRAMES = 2000;

    /** Counts quads and draws nothing. */
    private static final class CountingDraw implements UiDraw {
        private long quads;

        @Override
        public int width() {
            return 640;
        }

        @Override
        public int height() {
            return 360;
        }

        @Override
        public double guiScale() {
            return 2.0;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public int textWidth(String text) {
            return text.length() * 6;
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            quads++;
        }

        @Override
        public void gradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
            quads++;
        }

        @Override
        public void outline(int x, int y, int width, int height, int color) {
            quads += 4;
        }

        @Override
        public void text(String text, int x, int y, int color, boolean shadow) {
        }

        @Override
        public int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow) {
            return 0;
        }

        @Override
        public int wrappedHeight(String text, int maxWidth) {
            return 0;
        }

        @Override
        public void item(String itemId, String model, int x, int y) {
        }

        @Override
        public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
            quads++;
        }

        @Override
        public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int color) {
            quads++;
        }

        @Override
        public void pushScissor(int left, int top, int right, int bottom) {
        }

        @Override
        public void popScissor() {
        }

        @Override
        public void pushPose() {
        }

        @Override
        public void popPose() {
        }

        @Override
        public void translate(float x, float y) {
        }

        @Override
        public void scale(float x, float y) {
        }
    }

    private static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    @Test
    void aFrameAllocatesNothing() {
        ThemedBook book = ThemedBook.of(DressingRulesTest.book(0, 640, 360), ThemeFixtures.theme("herbalism", "rift"));
        TileFrame tile = new TileFrame();
        PageTheme frameTheme = ThemeFixtures.theme("nether", "seaborne");
        art.arcane.adapt.client.ui.kit.UiRect box = new art.arcane.adapt.client.ui.kit.UiRect(300, 120, 106, 22);
        CountingDraw draw = new CountingDraw();
        for (int frame = 0; frame < FRAMES; frame++) {
            long millis = frame * 50L;
            book.paintBack(draw, millis);
            book.paintFront(draw, millis);
            tile.paint(draw, frameTheme, box, true, millis, millis / 1000.0, false);
        }
        draw.quads = 0;
        long before = allocated();
        long started = System.nanoTime();
        for (int frame = FRAMES; frame < FRAMES * 2; frame++) {
            long millis = frame * 50L;
            book.paintBack(draw, millis);
            book.paintFront(draw, millis);
            tile.paint(draw, frameTheme, box, true, millis, millis / 1000.0, false);
        }
        long nanos = System.nanoTime() - started;
        long bytes = allocated() - before;
        System.out.printf("themed spread with a hovered mutated tile: %d quads a frame (%d of them the recorded layer), %.1f us a frame, %d bytes over %d frames%n",
                draw.quads / FRAMES, book.layerSize(), nanos / 1000.0 / FRAMES, bytes, FRAMES);
        assertTrue(bytes < FRAMES, "drawing allocates " + bytes + " bytes over " + FRAMES + " frames");
        assertTrue(draw.quads / FRAMES < 2500, "quads a frame: " + draw.quads / FRAMES);
    }
}
