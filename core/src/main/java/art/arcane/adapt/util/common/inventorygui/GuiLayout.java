package art.arcane.adapt.util.common.inventorygui;

public final class GuiLayout {
  public static final int WIDTH = 9;
  public static final int MAX_ROWS = 6;

  private GuiLayout() {
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
    return (WIDTH - count) / 2 + index - WIDTH / 2;
  }
}
