package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiTexture;

import java.util.Arrays;

/**
 * Fills and atlas blits recorded once and replayed every frame in order, so a static layer costs only its quads. Runs
 * of single pixels in one colour are merged into one fill as they are recorded.
 */
final class DrawList {
    private static final int FILL = 0;
    private static final int BLIT = 1;
    private static final int FIELDS = 9;

    private int[] ops = new int[FIELDS * 64];
    private UiTexture[] textures = new UiTexture[64];
    private int count;
    private int runX = Integer.MIN_VALUE;
    private int runY;
    private int runEnd;
    private int runColor;
    private boolean runVertical;

    private int[] slot(UiTexture texture) {
        if (count * FIELDS == ops.length) {
            ops = Arrays.copyOf(ops, ops.length * 2);
            textures = Arrays.copyOf(textures, textures.length * 2);
        }
        textures[count] = texture;
        return ops;
    }

    /** A fill from (x0, y0) to (x1, y1), right and bottom exclusive; empty boxes and clear colours are skipped. */
    void fill(int x0, int y0, int x1, int y1, int color) {
        flush();
        if (x1 <= x0 || y1 <= y0 || (color >>> 24) == 0) {
            return;
        }
        int[] into = slot(null);
        int at = count * FIELDS;
        into[at] = FILL;
        into[at + 1] = x0;
        into[at + 2] = y0;
        into[at + 3] = x1;
        into[at + 4] = y1;
        into[at + 5] = color;
        count++;
    }

    /** One pixel, merged with the pixels next to it along a row ({@code vertical} false) or a column. */
    void pixel(int x, int y, int color, boolean vertical) {
        if (runX != Integer.MIN_VALUE && color == runColor && vertical == runVertical
                && (vertical ? x == runX && y == runEnd : y == runY && x == runEnd)) {
            runEnd++;
            return;
        }
        flush();
        runX = x;
        runY = y;
        runEnd = vertical ? y + 1 : x + 1;
        runColor = color;
        runVertical = vertical;
    }

    private void flush() {
        if (runX == Integer.MIN_VALUE) {
            return;
        }
        int x = runX;
        runX = Integer.MIN_VALUE;
        if (runVertical) {
            fill(x, runY, x + 1, runEnd, runColor);
        } else {
            fill(x, runY, runEnd, runY + 1, runColor);
        }
    }

    /** The sprite at (x, y), cut to {@code width} by {@code height} from its top left. */
    void blit(UiTexture texture, ThemeSprite sprite, int x, int y, int width, int height) {
        flush();
        int w = Math.min(width, sprite.width());
        int h = Math.min(height, sprite.height());
        if (w <= 0 || h <= 0) {
            return;
        }
        int[] into = slot(texture);
        int at = count * FIELDS;
        into[at] = BLIT;
        into[at + 1] = x;
        into[at + 2] = y;
        into[at + 3] = w;
        into[at + 4] = h;
        into[at + 5] = sprite.u();
        into[at + 6] = sprite.v();
        count++;
    }

    void blit(UiTexture texture, ThemeSprite sprite, int x, int y) {
        blit(texture, sprite, x, y, sprite.width(), sprite.height());
    }

    /** Ends recording; later pixels start a new run. */
    void close() {
        flush();
    }

    int size() {
        return count;
    }

    void paint(UiDraw draw) {
        for (int index = 0; index < count; index++) {
            int at = index * FIELDS;
            if (ops[at] == FILL) {
                draw.fill(ops[at + 1], ops[at + 2], ops[at + 3], ops[at + 4], ops[at + 5]);
            } else {
                int w = ops[at + 3];
                int h = ops[at + 4];
                draw.blit(textures[index], ops[at + 1], ops[at + 2], w, h, ops[at + 5], ops[at + 6], w, h);
            }
        }
    }
}
