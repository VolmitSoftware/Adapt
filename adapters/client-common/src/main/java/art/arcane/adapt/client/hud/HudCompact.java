package art.arcane.adapt.client.hud;

import java.util.List;

/**
 * Finds a spot for a one-line pill on a small screen, in scaled GUI units. Rows are tried by distance from the
 * preferred position; a row is usable where the pill's band crosses no vanilla area (hotbar with status rows, held
 * item name and action bar; chat; boss bars; vanilla toasts and effects; scoreboard; crosshair) and no other drawn
 * widget, each kept {@link #MARGIN} apart. The first row with a gap of at least {@link #USEFUL_WIDTH} wins; failing
 * that the widest gap anywhere; failing that the preferred spot, marked crowded, so the pill is never dropped.
 * Reuses its buffers and does not allocate.
 */
final class HudCompact {
    static final double MARGIN = 2.0;
    static final double USEFUL_WIDTH = 80.0;
    static final double MIN_WIDTH = 18.0;
    private static final int CAPACITY = 64;

    private final double[] starts = new double[CAPACITY];
    private final double[] ends = new double[CAPACITY];
    private int count;
    private double bestX;
    private double bestWidth;
    private double bestDistance;
    private HudBox protectedZone;

    /**
     * Places a pill of up to {@code width} x {@code height} near {@code preferred} and writes it to {@code out} with
     * {@code scale}. Returns false when no free spot exists and the pill sits crowded at the preferred spot.
     */
    boolean place(HudBox preferred, double width, double height, double scale, double screenWidth, double screenHeight,
                  HudBox zone, List<HudRenderer.Placed> placed, int placedCount, HudRenderer.Placed self, HudBox out) {
        protectedZone = zone;
        double pillWidth = Math.min(width, screenWidth);
        double pillHeight = Math.min(height, screenHeight);
        int lastRow = (int) Math.floor(screenHeight - pillHeight);
        int preferredRow = (int) Math.max(0, Math.min(lastRow, Math.round(preferred.y)));
        double preferredX = Math.max(0.0, Math.min(screenWidth - pillWidth, preferred.x + (preferred.width - pillWidth) / 2.0));
        for (int distance = 0; distance <= lastRow; distance++) {
            for (int side = 0; side < 2; side++) {
                int row = side == 0 ? preferredRow + distance : preferredRow - distance;
                if (row < 0 || row > lastRow || side == 1 && distance == 0) {
                    continue;
                }
                if (gap(row, pillHeight, Math.min(pillWidth, USEFUL_WIDTH), pillWidth, preferredX, screenWidth, screenHeight, placed, placedCount, self)) {
                    out.set(bestX, row, bestWidth, pillHeight, scale);
                    return true;
                }
            }
        }
        double widest = 0.0;
        int widestRow = -1;
        double widestX = 0.0;
        for (int row = 0; row <= lastRow; row++) {
            if (gap(row, pillHeight, Math.min(pillWidth, MIN_WIDTH), pillWidth, preferredX, screenWidth, screenHeight, placed, placedCount, self)
                    && bestWidth > widest) {
                widest = bestWidth;
                widestRow = row;
                widestX = bestX;
            }
        }
        if (widestRow >= 0) {
            out.set(widestX, widestRow, widest, pillHeight, scale);
            return true;
        }
        out.set(Math.round(preferredX), preferredRow, pillWidth, pillHeight, scale);
        return false;
    }

    private boolean gap(int row, double height, double need, double want, double preferredX, double screenWidth,
                        double screenHeight, List<HudRenderer.Placed> placed, int placedCount, HudRenderer.Placed self) {
        count = 0;
        double top = row;
        double bottom = row + height;
        double half = screenWidth / 2.0;
        block(top, bottom, half - 91, screenHeight - 72, 182, 72);
        block(top, bottom, 0, screenHeight - 130, Math.min(330, half - 91), 90);
        block(top, bottom, half - 91, 0, 182, 50);
        block(top, bottom, screenWidth - 160, 0, 160, 52);
        block(top, bottom, screenWidth - 110, screenHeight / 2.0 - 60, 110, 120);
        block(top, bottom, half - 8, screenHeight / 2.0 - 8, 16, 16);
        if (protectedZone != null && protectedZone.width > 0.0) {
            block(top, bottom, protectedZone.x, protectedZone.y, protectedZone.width, protectedZone.height);
        }
        for (int index = 0; index < placedCount; index++) {
            HudRenderer.Placed other = placed.get(index);
            if (other != self && other.drawn) {
                block(top, bottom, other.box.x, other.box.y, other.box.width, other.box.height);
            }
        }
        sort();
        bestDistance = Double.MAX_VALUE;
        bestWidth = 0.0;
        double cursor = 0.0;
        for (int index = 0; index <= count; index++) {
            double end = index == count ? screenWidth : starts[index];
            consider(cursor, end, need, want, preferredX);
            if (index < count) {
                cursor = Math.max(cursor, ends[index]);
            }
        }
        return bestWidth > 0.0;
    }

    private void consider(double start, double end, double need, double want, double preferredX) {
        double left = Math.ceil(start);
        double room = Math.floor(end) - left;
        if (room < need || room <= 0.0) {
            return;
        }
        double width = Math.min(want, room);
        double x = Math.max(left, Math.min(Math.floor(end) - width, Math.round(preferredX)));
        double distance = Math.abs(x - preferredX);
        if (distance < bestDistance) {
            bestDistance = distance;
            bestWidth = width;
            bestX = x;
        }
    }

    private void block(double top, double bottom, double x, double y, double width, double height) {
        if (width <= 0.0 || height <= 0.0 || count == CAPACITY) {
            return;
        }
        if (y - MARGIN < bottom && top < y + height + MARGIN) {
            starts[count] = x - MARGIN;
            ends[count] = x + width + MARGIN;
            count++;
        }
    }

    private void sort() {
        for (int index = 1; index < count; index++) {
            double start = starts[index];
            double end = ends[index];
            int cursor = index - 1;
            while (cursor >= 0 && starts[cursor] > start) {
                starts[cursor + 1] = starts[cursor];
                ends[cursor + 1] = ends[cursor];
                cursor--;
            }
            starts[cursor + 1] = start;
            ends[cursor + 1] = end;
        }
    }
}
