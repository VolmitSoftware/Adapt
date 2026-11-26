package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;

/**
 * The spread's moments of motion, drawn over both pages in the themed books' wisp style (a pale head with a halo and a
 * fading tail in its parent's colour). A swap sends one wisp across the fold each way. The merge draws wisps from all
 * over both pages into the spine, where a seam brightens, then lays the paper over the pages before the new page opens.
 */
final class SpreadVeil extends UiNode {
    static final long SWAP_MILLIS = 450L;
    static final long MERGE_MILLIS = 1000L;
    private static final int SPARK = 0xFFFFF6D8;
    private static final int TRAIL = 14;
    private static final int MERGE_WISPS = 9;

    private long swapAt = -1L;
    private long mergeAt = -1L;
    private int fold;
    private int leadColor;
    private int partnerColor;
    private int paper;

    SpreadVeil(String id) {
        super(id);
    }

    void colors(int lead, int partner, int paperColor) {
        leadColor = lead;
        partnerColor = partner;
        paper = paperColor;
    }

    void swap(long at) {
        swapAt = at;
    }

    void merge(long at) {
        mergeAt = at;
    }

    boolean merging() {
        return mergeAt >= 0L;
    }

    /** Whether the merge has run its course at {@code at}. */
    boolean merged(long at) {
        return mergeAt >= 0L && at - mergeAt >= MERGE_MILLIS;
    }

    void place(UiRect box, int foldX, UiDraw metrics) {
        fold = foldX;
        place(box, metrics);
    }

    @Override
    public String kind() {
        return "veil";
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        long at = frame.motion().now();
        if (swapAt >= 0L && at - swapAt < SWAP_MILLIS) {
            paintSwap(draw, (at - swapAt) / (double) SWAP_MILLIS);
        }
        if (mergeAt >= 0L) {
            paintMerge(draw, Math.min(1.0D, (at - mergeAt) / (double) MERGE_MILLIS));
        }
    }

    private void paintSwap(UiDraw draw, double t) {
        UiRect box = bounds();
        int span = Math.max(8, Math.min(fold - box.x(), box.right() - fold) - 12);
        int midY = box.y() + box.height() / 3;
        for (int j = TRAIL - 1; j >= 0; j--) {
            double q = t - j * 0.02;
            if (q < 0) {
                continue;
            }
            double e = ease(q);
            double arc = Math.sin(e * Math.PI) * 18;
            int fade = (int) (255 * Math.sin(Math.min(1.0D, t) * Math.PI) * (1 - j / (double) TRAIL));
            mote(draw, (int) Math.round(fold - span + e * span * 2), (int) Math.round(midY - arc), leadColor, fade, j);
            mote(draw, (int) Math.round(fold + span - e * span * 2), (int) Math.round(midY + arc), partnerColor, fade, j);
        }
    }

    private void paintMerge(UiDraw draw, double t) {
        UiRect box = bounds();
        int midY = box.y() + box.height() / 2;
        double pull = Math.min(1.0D, t / 0.7D);
        for (int index = 0; index < MERGE_WISPS; index++) {
            double seedY = ((index * 37) % MERGE_WISPS) / (double) (MERGE_WISPS - 1);
            double seedX = 0.35D + ((index * 53) % 7) / 10.0D;
            int fromY = box.y() + 8 + (int) Math.round(seedY * (box.height() - 16));
            double delay = (index % 3) * 0.08D;
            for (int j = TRAIL - 1; j >= 0; j--) {
                double q = (pull - delay) / (1 - delay) - j * 0.025;
                if (q <= 0) {
                    continue;
                }
                double e = ease(Math.min(1.0D, q));
                double curl = Math.sin(e * Math.PI * 2 + index) * 6 * (1 - e);
                int fade = (int) (255 * Math.min(1.0D, (1 - t) * 3) * (1 - j / (double) TRAIL));
                int leftX = (int) Math.round(box.x() + (fold - box.x()) * (1 - seedX) + (fold - (box.x() + (fold - box.x()) * (1 - seedX))) * e);
                int rightX = 2 * fold - leftX;
                int y = (int) Math.round(fromY + (midY - fromY) * e + curl);
                mote(draw, leftX, y, leadColor, fade, j);
                mote(draw, rightX, 2 * midY - y, partnerColor, fade, j);
            }
        }
        double seam = Math.min(1.0D, t / 0.6D);
        int seamAlpha = (int) (200 * seam * Math.min(1.0D, (1 - t) * 2.5D));
        int reach = (int) Math.round(box.height() / 2.0D * seam);
        draw.fill(fold - 2, midY - reach, fold + 3, midY + reach, (seamAlpha / 3 << 24) | (SPARK & 0xFFFFFF));
        draw.fill(fold, midY - reach, fold + 1, midY + reach, (seamAlpha << 24) | (SPARK & 0xFFFFFF));
        if (t > 0.6D) {
            int cover = (int) (230 * Math.min(1.0D, (t - 0.6D) / 0.4D));
            draw.fill(box.x(), box.y(), box.right(), box.bottom(), (cover << 24) | (paper & 0xFFFFFF));
        }
    }

    private static void mote(UiDraw draw, int x, int y, int color, int alpha, int j) {
        if (alpha <= 0) {
            return;
        }
        int a = Math.min(255, alpha) << 24;
        int size = j < 5 ? 2 : 1;
        draw.fill(x, y, x + size, y + size, a | ((j < 2 ? SPARK : color) & 0xFFFFFF));
        if (j == 0) {
            int halo = (Math.min(255, alpha) * 2 / 5) << 24 | (color & 0xFFFFFF);
            draw.fill(x - 1, y, x + 3, y + 2, halo);
            draw.fill(x, y - 1, x + 2, y + 3, halo);
        }
    }

    private static double ease(double t) {
        return t < 0.5D ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }
}
