package art.arcane.adapt.util.common.inventorygui;

public final class GuiLayout {
  public static final int WIDTH = 9;
  public static final int FIVE_WIDTH = 5;
  public static final int MAX_ROWS = 6;
  private static final int[][] CENTERED_POSITIONS = {
      {},
      {0},
      {-1, 1},
      {-1, 0, 1},
      {-2, -1, 1, 2},
      {-2, -1, 0, 1, 2},
      {-3, -2, -1, 1, 2, 3},
      {-3, -2, -1, 0, 1, 2, 3},
      {-4, -3, -2, -1, 1, 2, 3, 4},
      {-4, -3, -2, -1, 0, 1, 2, 3, 4}
  };
  private static final int[][] FIVE_ROW_POSITIONS = {
      {},
      {2},
      {1, 3},
      {1, 2, 3},
      {0, 1, 3, 4},
      {0, 1, 2, 3, 4}
  };

  private GuiLayout() {
  }

  public static PagePlan plan(int totalItems, boolean reserveNavigationRow) {
    int items = Math.max(0, totalItems);
    boolean navigation = reserveNavigationRow;
    int maxContentRows = MAX_ROWS - (navigation ? 1 : 0);
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

  public static PagePlan planFiveWide(int totalItems, int configuredRows) {
    int items = Math.max(0, totalItems);
    int requested = Math.max(0, Math.min(MAX_ROWS, configuredRows));
    int contentRows;
    if (requested > 0) {
      contentRows = Math.max(1, requested - 1);
    } else if (items <= 0) {
      contentRows = 1;
    } else {
      contentRows = Math.min(MAX_ROWS - 1, (int) Math.ceil(items / (double) FIVE_WIDTH));
    }

    int itemsPerPage = contentRows * FIVE_WIDTH;
    int pages = Math.max(1, (int) Math.ceil(items / (double) itemsPerPage));
    return new PagePlan(contentRows + 1, contentRows, true, itemsPerPage, pages);
  }

  public static int fiveWideRowsForPage(int pageItems, int configuredRows) {
    int requested = Math.max(0, Math.min(MAX_ROWS, configuredRows));
    if (requested > 0) {
      return Math.max(2, requested);
    }

    int used = pageItems <= 0 ? 1 : (int) Math.ceil(pageItems / (double) FIVE_WIDTH);
    return Math.max(2, Math.min(MAX_ROWS, used + 1));
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
    return CENTERED_POSITIONS[count][index];
  }

  public static int spacedFivePosition(int indexInRow, int rowCount) {
    int count = Math.max(1, Math.min(5, rowCount));
    int index = Math.max(0, Math.min(count - 1, indexInRow));
    return -(count - 1) + (index * 2);
  }

  public static int centeredFiveRow(int index, int rowCount, int availableRows) {
    int span = Math.max(1, Math.min(MAX_ROWS, availableRows));
    int count = Math.max(1, Math.min(span, Math.min(FIVE_WIDTH, rowCount)));
    int safeIndex = Math.max(0, Math.min(count - 1, index));
    if (span == FIVE_WIDTH) {
      return FIVE_ROW_POSITIONS[count][safeIndex];
    }

    return ((span - count) / 2) + safeIndex;
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
