package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.MenuSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The frame every Adapt chest menu shares: a header bar with the menu's subject in the middle, one to four body rows
 * and a footer bar whose controls sit in fixed {@link Slot}s. Only the two bars carry filler; empty body slots stay
 * empty. Columns run 0 to 8 and body rows count from the first row under the header.
 */
public final class GuiFrame {
  public static final int COLUMNS = GuiLayout.WIDTH;
  public static final int MAX_BODY_ROWS = GuiLayout.MAX_ROWS - 2;
  public static final int SUBJECT_COLUMN = 4;
  /** Items a labelled group fits on one row: column 0 holds the group's heading. */
  public static final int LABELLED_WIDTH = COLUMNS - 1;
  static final Id BAR = Id.minecraft("black_stained_glass_pane");
  private static final Id BACK = Id.minecraft("arrow");
  private static final Id TURN = Id.minecraft("spectral_arrow");
  private static final Id PAGE = Id.minecraft("paper");
  private static volatile MenusOffer menusOffer;

  private GuiFrame() {
  }

  /** The footer's fixed controls, by column. */
  public enum Slot {
    BACK(0),
    TOOLS(1),
    LIST(2),
    PREVIOUS(3),
    PAGE(4),
    NEXT(5),
    SETTINGS(6),
    ACTION(7),
    MENUS(8);

    private final int column;

    Slot(int column) {
      this.column = column;
    }

    public int column() {
      return column;
    }
  }

  /** The chest height of a frame with {@code bodyRows} body rows, clamped to 1..{@link #MAX_BODY_ROWS}. */
  public static int rows(int bodyRows) {
    return clampBody(bodyRows) + 2;
  }

  /** Sizes the window for {@code bodyRows} body rows and fills the header and footer bars. */
  public static void apply(GuiWindow window, String tag, int bodyRows) {
    window.setTag(tag);
    window.setViewportHeight(rows(bodyRows));
    window.setDecorator((GuiWindow framed, int position, int row) -> row == 0 || row == framed.getViewportHeight() - 1
        ? GuiTheme.element("bar-" + row + "-" + position, BAR, "gui", "background", row == 0 ? "header" : "footer")
            .setName(" ").setRole(MenuRole.DECORATION)
        : null);
    GuiElement offer = offer(window);
    if (offer != null) {
      footer(window, Slot.MENUS, offer);
    }
  }

  /**
   * Sets what the {@link Slot#MENUS} slot of every chest menu offers its viewer, such as a way to the book menus; null
   * leaves the slot to the footer bar.
   */
  public static void setMenusOffer(MenusOffer offer) {
    menusOffer = offer;
  }

  private static GuiElement offer(GuiWindow window) {
    MenusOffer offer = menusOffer;
    if (offer == null) {
      return null;
    }
    try {
      return offer.element(window.getViewer());
    } catch (RuntimeException failure) {
      AdaptLog.warn("The chest menu offer failed for " + window.getViewer().name() + ": " + failure);
      return null;
    }
  }

  public static void subject(GuiWindow window, GuiElement element) {
    window.setElement(SUBJECT_COLUMN - 4, 0, element);
  }

  public static void footer(GuiWindow window, Slot slot, GuiElement element) {
    window.setElement(slot.column() - 4, window.getViewportHeight() - 1, element);
  }

  /** Puts an element on body row {@code row} (0 is the row under the header), column {@code column}. */
  public static void body(GuiWindow window, int row, int column, GuiElement element) {
    window.setElement(column - 4, row + 1, element);
  }

  /** Places each cell of {@code page} through {@code render}; a null element leaves the slot empty. */
  public static <T> void body(GuiWindow window, Page<T> page, CellRenderer<T> render) {
    for (Cell<T> cell : page.cells()) {
      GuiElement element = render.render(cell);
      if (element != null) {
        body(window, cell.row(), cell.column(), element);
      }
    }
  }

  /** The Back control in its slot, unless the server hides Back buttons. */
  public static void back(GuiWindow window, Runnable action) {
    if (!AdaptConfig.get().isGuiBackButton()) {
      return;
    }
    footer(window, Slot.BACK, GuiTheme.element("back", BACK, "gui", "navigation", "back")
        .setName(C.WHITE + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
        .setRole(MenuRole.NAVIGATION)
        .onLeftClick((MenuClick click) -> {
          MenuSounds.play(window.getViewer(), MenuSounds.Action.BACK);
          action.run();
        }));
  }

  /**
   * Previous, page and next in their slots when there is more than one page; each turn opens the page through
   * {@code open}. Also tells native clients the paging.
   */
  public static void pager(GuiWindow window, int page, int pages, int total, IntConsumer open) {
    window.setPaging(new MenuPaging(page, pages, total));
    if (pages <= 1) {
      return;
    }
    if (page > 0) {
      footer(window, Slot.PREVIOUS, GuiTheme.element("page-previous", TURN, "gui", "navigation", "previous")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .setRole(MenuRole.NAVIGATION)
          .onLeftClick((MenuClick click) -> {
            MenuSounds.play(window.getViewer(), MenuSounds.Action.TURN);
            open.accept(page - 1);
          }));
    }
    footer(window, Slot.PAGE, GuiTheme.element("page-info", PAGE, "gui", "navigation", "page")
        .setName(C.GRAY + AdaptLanguage.text(GuiMessages.PAGE_OF, trusted("page", page + 1), trusted("pages", pages)))
        .setCount(page + 1)
        .setHint(MenuHint.navigation("page-info"))
        .setRole(MenuRole.INFO));
    if (page < pages - 1) {
      footer(window, Slot.NEXT, GuiTheme.element("page-next", TURN, "gui", "navigation", "next")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .setRole(MenuRole.NAVIGATION)
          .onLeftClick((MenuClick click) -> {
            MenuSounds.play(window.getViewer(), MenuSounds.Action.TURN);
            open.accept(page + 1);
          }));
    }
  }

  /**
   * {@code items} in rows of up to {@code width}, each row centred; a null item keeps its place empty. Pages hold
   * {@link #MAX_BODY_ROWS} rows.
   */
  public static <T> Layout<T> grid(List<T> items, int width) {
    return grid(items, width, MAX_BODY_ROWS);
  }

  /** {@link #grid(List, int)} with pages of {@code rowsPerPage} rows (clamped to 1..{@link #MAX_BODY_ROWS}). */
  public static <T> Layout<T> grid(List<T> items, int width, int rowsPerPage) {
    int pageRows = clampBody(rowsPerPage);
    int perRow = Math.max(1, Math.min(COLUMNS, width));
    List<List<T>> rows = new ArrayList<>();
    for (int from = 0; from < items.size(); from += perRow) {
      rows.add(items.subList(from, Math.min(items.size(), from + perRow)));
    }
    List<Page<T>> pages = new ArrayList<>();
    for (int from = 0; from < rows.size(); from += pageRows) {
      List<Cell<T>> cells = new ArrayList<>();
      List<List<T>> page = rows.subList(from, Math.min(rows.size(), from + pageRows));
      for (int row = 0; row < page.size(); row++) {
        List<T> line = page.get(row);
        int start = (COLUMNS - line.size()) / 2;
        for (int index = 0; index < line.size(); index++) {
          if (line.get(index) != null) {
            cells.add(new Cell<>(row, start + index, 0, line.get(index)));
          }
        }
      }
      pages.add(new Page<>(cells, page.size()));
    }
    return Layout.of(pages);
  }

  /**
   * Groups of items, each group starting on a new row. Labelled groups put a heading cell (null item) in column 0 of
   * their first row and run their items left to right from column 1; unlabelled groups centre each row across all nine
   * columns. A group stays on one page unless it alone is taller than a page.
   */
  public static <T> Layout<T> groups(List<List<T>> groups, boolean labelled) {
    int width = labelled ? LABELLED_WIDTH : COLUMNS;
    List<List<Cell<T>>> rows = new ArrayList<>();
    List<Integer> groupStarts = new ArrayList<>();
    for (int group = 0; group < groups.size(); group++) {
      List<T> items = groups.get(group);
      if (items.isEmpty()) {
        continue;
      }
      groupStarts.add(rows.size());
      for (int from = 0; from < items.size(); from += width) {
        List<T> line = items.subList(from, Math.min(items.size(), from + width));
        List<Cell<T>> cells = new ArrayList<>(line.size() + 1);
        int start = labelled ? 1 : (COLUMNS - line.size()) / 2;
        if (labelled && from == 0) {
          cells.add(new Cell<>(0, 0, group, null));
        }
        for (int index = 0; index < line.size(); index++) {
          cells.add(new Cell<>(0, start + index, group, line.get(index)));
        }
        rows.add(cells);
      }
    }
    groupStarts.add(rows.size());
    List<Page<T>> pages = new ArrayList<>();
    List<List<Cell<T>>> current = new ArrayList<>();
    for (int index = 0; index + 1 < groupStarts.size(); index++) {
      List<List<Cell<T>>> groupRows = rows.subList(groupStarts.get(index), groupStarts.get(index + 1));
      if (current.size() + groupRows.size() > MAX_BODY_ROWS && !current.isEmpty()) {
        pages.add(page(current));
        current = new ArrayList<>();
      }
      for (List<Cell<T>> row : groupRows) {
        if (current.size() == MAX_BODY_ROWS) {
          pages.add(page(current));
          current = new ArrayList<>();
        }
        current.add(row);
      }
    }
    if (!current.isEmpty()) {
      pages.add(page(current));
    }
    return Layout.of(pages);
  }

  private static <T> Page<T> page(List<List<Cell<T>>> rows) {
    List<Cell<T>> cells = new ArrayList<>();
    for (int row = 0; row < rows.size(); row++) {
      for (Cell<T> cell : rows.get(row)) {
        cells.add(new Cell<>(row, cell.column(), cell.group(), cell.item()));
      }
    }
    return new Page<>(cells, rows.size());
  }

  private static int clampBody(int bodyRows) {
    return Math.max(1, Math.min(MAX_BODY_ROWS, bodyRows));
  }

  /** One body slot: its row and column, the index of the group it belongs to, and its item (null for a heading). */
  public record Cell<T>(int row, int column, int group, T item) {
    public boolean heading() {
      return item == null;
    }
  }

  /** One page of body cells and the rows it uses. */
  public record Page<T>(List<Cell<T>> cells, int rows) {
    public Page {
      cells = List.copyOf(cells);
    }

    /** The items on this page in reading order, headings left out. */
    public List<T> items() {
      List<T> items = new ArrayList<>(cells.size());
      for (Cell<T> cell : cells) {
        if (!cell.heading()) {
          items.add(cell.item());
        }
      }
      return items;
    }
  }

  /**
   * The pages of a body and the body height every page is drawn at (the tallest page's, at least 1), so the chest
   * keeps its size while paging.
   */
  public record Layout<T>(List<Page<T>> pages, int bodyRows) {
    static <T> Layout<T> of(List<Page<T>> pages) {
      List<Page<T>> shown = pages.isEmpty() ? List.of(new Page<>(List.of(), 1)) : pages;
      int rows = 1;
      for (Page<T> page : shown) {
        rows = Math.max(rows, page.rows());
      }
      return new Layout<>(List.copyOf(shown), clampBody(rows));
    }

    public int pageCount() {
      return pages.size();
    }

    /** The page at {@code index}, clamped to the pages there are. */
    public Page<T> page(int index) {
      return pages.get(clampedPage(index));
    }

    public int clampedPage(int index) {
      return Math.max(0, Math.min(pages.size() - 1, index));
    }
  }

  /** Supplies the {@link Slot#MENUS} item for a viewer, or null for none. Called each time a chest menu is framed. */
  @FunctionalInterface
  public interface MenusOffer {
    GuiElement element(PlayerRef viewer);
  }

  @FunctionalInterface
  public interface CellRenderer<T> {
    GuiElement render(Cell<T> cell);
  }
}
