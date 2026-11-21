package art.arcane.adapt.client.ui.kit;

import java.util.ArrayList;
import java.util.List;

/** Layout arithmetic shared by widgets and screens. A size of 0 in {@link #row} and {@link #column} means "share the rest". */
public final class UiLayout {
    private UiLayout() {
    }

    /** Splits {@code area} left to right; fixed widths are kept, zero widths share what is left. */
    public static List<UiRect> row(UiRect area, int gap, int... widths) {
        int[] sizes = distribute(area.width(), gap, widths);
        List<UiRect> cells = new ArrayList<>(sizes.length);
        int x = area.x();
        for (int size : sizes) {
            cells.add(new UiRect(x, area.y(), size, area.height()));
            x += size + gap;
        }
        return cells;
    }

    /** Splits {@code area} top to bottom; fixed heights are kept, zero heights share what is left. */
    public static List<UiRect> column(UiRect area, int gap, int... heights) {
        int[] sizes = distribute(area.height(), gap, heights);
        List<UiRect> cells = new ArrayList<>(sizes.length);
        int y = area.y();
        for (int size : sizes) {
            cells.add(new UiRect(area.x(), y, area.width(), size));
            y += size + gap;
        }
        return cells;
    }

    /** {@code count} cells of a fixed size, filled row by row from the top left of {@code area}. */
    public static List<UiRect> grid(UiRect area, int columns, int cellWidth, int cellHeight, int gap, int count) {
        List<UiRect> cells = new ArrayList<>(count);
        int perRow = Math.max(1, columns);
        for (int index = 0; index < count; index++) {
            int column = index % perRow;
            int row = index / perRow;
            cells.add(new UiRect(area.x() + column * (cellWidth + gap), area.y() + row * (cellHeight + gap), cellWidth, cellHeight));
        }
        return cells;
    }

    /** Total size of {@code count} cells of {@code cell} units separated by {@code gap}. */
    public static int span(int count, int cell, int gap) {
        return count <= 0 ? 0 : count * cell + (count - 1) * gap;
    }

    /** The largest cell size in [min, max] that fits {@code count} cells and their gaps into {@code available}; min if none fits. */
    public static int fit(int available, int count, int gap, int min, int max) {
        if (count <= 0) {
            return max;
        }
        int cell = (available - (count - 1) * gap) / count;
        return Math.max(min, Math.min(max, cell));
    }

    /** The widest a window {@code height} tall may be: 16:9 at most. */
    public static int widest(int height) {
        return Math.max(0, height) * 16 / 9;
    }

    /** The shortest a window {@code width} wide may be to stay within 16:9. */
    public static int shortest(int width) {
        return (Math.max(0, width) * 9 + 15) / 16;
    }

    /** A rectangle of the given size centred in {@code area}, shrunk to fit it. */
    public static UiRect center(UiRect area, int width, int height) {
        int w = Math.min(width, area.width());
        int h = Math.min(height, area.height());
        return new UiRect(area.x() + (area.width() - w) / 2, area.y() + (area.height() - h) / 2, w, h);
    }

    private static int[] distribute(int total, int gap, int[] sizes) {
        int[] result = new int[sizes.length];
        int fixed = 0;
        int flexible = 0;
        for (int size : sizes) {
            if (size > 0) {
                fixed += size;
            } else {
                flexible++;
            }
        }
        int free = Math.max(0, total - fixed - Math.max(0, sizes.length - 1) * gap);
        int share = flexible == 0 ? 0 : free / flexible;
        int remainder = flexible == 0 ? 0 : free % flexible;
        for (int index = 0; index < sizes.length; index++) {
            if (sizes[index] > 0) {
                result[index] = sizes[index];
            } else {
                result[index] = share + (remainder-- > 0 ? 1 : 0);
            }
        }
        return result;
    }
}
