package art.arcane.adapt.client.protocol;

import java.util.List;
import java.util.Objects;

/**
 * A core {@code ChestMenu} as the native generic panel draws it. {@code sessionId} names the open menu;
 * {@code revision} increases on every update so clicks against a stale layout can be ignored. {@code paging} is null
 * for a menu with one page; {@code breadcrumb} names the menus above this one, outermost first.
 */
public record PanelView(int sessionId, int revision, String title, int rows, PanelLayout layout, PanelPaging paging,
                        List<String> breadcrumb, List<PanelTile> tiles) {
  public static final int COLUMNS = 9;

  public PanelView {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(layout, "layout");
    breadcrumb = List.copyOf(breadcrumb);
    tiles = List.copyOf(tiles);
    if (rows < 1 || rows > FrameLimits.MAX_PANEL_ROWS) {
      throw new IllegalArgumentException("rows must be 1.." + FrameLimits.MAX_PANEL_ROWS + ": " + rows);
    }
    for (PanelTile tile : tiles) {
      if (tile.slot() < 0 || tile.slot() >= rows * COLUMNS) {
        throw new IllegalArgumentException("tile slot " + tile.slot() + " outside a panel of " + rows + " rows");
      }
    }
  }

  public PanelView withRevision(int newRevision) {
    return new PanelView(sessionId, newRevision, title, rows, layout, paging, breadcrumb, tiles);
  }
}
