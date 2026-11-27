package art.arcane.adapt.content.golem.gui;

public final class GolemLayout {
  public static final int WIDTH = 9;
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

  private GolemLayout() {
  }

  public static PagePlan plan(int totalItems, boolean reserveNavigationRow) {
    return planWithSettings(totalItems, reserveNavigationRow, false);
  }

  public static PagePlan planWithSettings(int totalItems, boolean reserveNavigationRow, boolean reserveSettingsRow) {
    int items = Math.max(0, totalItems);
    int availableRows = MAX_ROWS - (reserveSettingsRow ? 1 : 0);
    boolean navigation = reserveNavigationRow;
    int maxContentRows = availableRows - (navigation ? 1 : 0);
    if (items > maxContentRows * WIDTH) {
      navigation = true;
      maxContentRows = availableRows - 1;
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
    int rows = contentRows + (navigation ? 1 : 0) + (reserveSettingsRow ? 1 : 0);
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
    return CENTERED_POSITIONS[count][index];
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
