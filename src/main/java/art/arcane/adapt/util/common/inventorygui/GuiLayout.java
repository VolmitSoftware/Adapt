package art.arcane.adapt.util.common.inventorygui;

public final class GuiLayout {
    public static final int WIDTH = 9;
    public static final int MAX_ROWS = 6;

    private GuiLayout() {
    }

    public static PagePlan plan(int totalItems, boolean reserveNavigationRow) {
        int items = Math.max(0, totalItems);

        boolean navigation = reserveNavigationRow;
        int maxContentRows = MAX_ROWS - (navigation ? 1 : 0);
        if (maxContentRows < 1) {
            maxContentRows = 1;
        }

        if (items > maxContentRows * WIDTH) {
            navigation = true;
            maxContentRows = MAX_ROWS - 1;
        }

        int contentRows;
        if (items <= 0) {
            contentRows = 1;
        } else if (items > maxContentRows * WIDTH) {
            contentRows = maxContentRows;
        } else {
            contentRows = (int) Math.ceil(items / (double) WIDTH);
        }

        contentRows = Math.max(1, Math.min(maxContentRows, contentRows));
        int rows = contentRows + (navigation ? 1 : 0);
        rows = Math.max(1, Math.min(MAX_ROWS, rows));

        int itemsPerPage = contentRows * WIDTH;
        itemsPerPage = Math.max(WIDTH, itemsPerPage);
        int pages = Math.max(1, (int) Math.ceil(items / (double) itemsPerPage));

        return new PagePlan(rows, contentRows, navigation, itemsPerPage, pages);
    }

    public static int clampPage(int page, int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(pageCount - 1, page));
    }

    public static int centeredPosition(int indexInRow, int rowCount) {
        int count = Math.max(1, Math.min(WIDTH, rowCount));
        int index = Math.max(0, Math.min(count - 1, indexInRow));
        int start = -(count / 2);
        return start + index;
    }

    public record PagePlan(
            int rows,
            int contentRows,
            boolean hasNavigationRow,
            int itemsPerPage,
            int pageCount
    ) {
    }
}
