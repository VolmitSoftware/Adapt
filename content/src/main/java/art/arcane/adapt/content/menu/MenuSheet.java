package art.arcane.adapt.content.menu;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A built-in chest menu laid out in the coordinates Adapt's menus use: {@code position} runs from -4 (left column) to
 * 4 (right column), rows count from the top. A themed sheet fills every empty slot with Adapt's background panes.
 */
public final class MenuSheet {
  public static final int WIDTH = ChestMenu.COLUMNS;
  private static final int CENTER = WIDTH / 2;
  private static final Id BLACK_PANE = Id.minecraft("black_stained_glass_pane");
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");
  private static final Id LIGHT_GRAY_PANE = Id.minecraft("light_gray_stained_glass_pane");
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

  private final boolean themed;
  private final Map<Integer, MenuEntry> entries = new HashMap<>();
  private String title = "";
  private int rows;
  private @Nullable MenuLayout layout;

  private MenuSheet(boolean themed, int rows) {
    this.themed = themed;
    this.rows = rows;
  }

  /** A sheet with Adapt's background panes behind its entries. */
  public static MenuSheet themed(int rows) {
    return new MenuSheet(true, clampRows(rows));
  }

  /** A sheet whose empty slots stay empty. */
  public static MenuSheet plain(int rows) {
    return new MenuSheet(false, clampRows(rows));
  }

  /** The position of entry {@code index} of {@code count} entries spread around the middle column of one row. */
  public static int centeredPosition(int index, int count) {
    int clamped = Math.max(1, Math.min(WIDTH, count));
    return CENTERED_POSITIONS[clamped][Math.max(0, Math.min(clamped - 1, index))];
  }

  public MenuSheet title(String text) {
    this.title = text == null ? "" : text;
    return this;
  }

  public MenuSheet rows(int count) {
    this.rows = clampRows(count);
    return this;
  }

  public MenuSheet layout(MenuLayout newLayout) {
    this.layout = newLayout;
    return this;
  }

  /** Places the entry; a position outside -4..4 is ignored, as is a row below the sheet when it is built. */
  public MenuSheet set(int position, int row, MenuEntry entry) {
    if (position >= -CENTER && position <= CENTER && row >= 0 && row < ChestMenu.MAX_ROWS) {
      entries.put((row * WIDTH) + position + CENTER, entry);
    }
    return this;
  }

  public ChestMenu build() {
    ChestMenu.Builder builder = ChestMenu.builder(title, rows);
    for (int row = 0; row < rows; row++) {
      for (int position = -CENTER; position <= CENTER; position++) {
        MenuEntry entry = entries.get((row * WIDTH) + position + CENTER);
        if (entry == null && themed) {
          entry = MenuEntry.modelled(background(row, position), "gui", "background", backgroundKey(row, position))
              .name(" ").role(MenuRole.DECORATION);
        }
        if (entry != null) {
          builder.set(row, position + CENTER, entry.button());
        }
      }
    }
    return builder.layout(layout).build();
  }

  /** Closes the menu the click came from, if it is still open. */
  public static void close(MenuClick click) {
    MenuSession session = click.session();
    if (session != null && session.isOpen()) {
      session.close();
    }
  }

  /** Builds the sheet and shows it to {@code viewer}. */
  public MenuSession open(PlayerRef viewer) {
    return AdaptApi.platform().menus().open(viewer, build());
  }

  private String backgroundKey(int row, int position) {
    if (row == 0 && rows >= 3) {
      return position % 2 == 0 ? "header-even" : "header-odd";
    }
    if (row == 1 && rows >= 4) {
      return "separator";
    }
    return position % 2 == 0 ? "body-even" : "body-odd";
  }

  private Id background(int row, int position) {
    if (row == 0 && rows >= 3) {
      return position % 2 == 0 ? GRAY_PANE : LIGHT_GRAY_PANE;
    }
    if (row == 1 && rows >= 4) {
      return BLACK_PANE;
    }
    return position % 2 == 0 ? BLACK_PANE : GRAY_PANE;
  }

  private static int clampRows(int count) {
    return Math.max(1, Math.min(ChestMenu.MAX_ROWS, count));
  }
}
