package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Golemancy's chest menu builder, with the coordinates of the inventorygui windows the Bukkit menus used: {@code position} runs
 * from -4 (left column) to 4 (right column), rows count from the top. Empty slots are filled by the decorator, the
 * viewport height is the chest row count. {@link #open()} renders it through the platform {@link MenuHost};
 * {@link #update()} re-renders an open window in place.
 */
public final class GolemWindow {
  private static final int CENTER = GolemLayout.WIDTH / 2;

  private final PlayerRef viewer;
  private final Map<Integer, GolemElement> elements = new HashMap<>();
  private Decorator decorator;
  private String tag;
  private String title = "";
  private int viewportHeight = GolemLayout.MAX_ROWS;
  private boolean freePlayerInventory;
  private MenuLayout layout;
  private MenuPaging paging;
  private List<String> breadcrumb = List.of();
  private Consumer<GolemWindow> onClosed;
  private MenuSession session;
  private ChestMenu rendered;

  public GolemWindow(PlayerRef viewer) {
    this.viewer = Objects.requireNonNull(viewer, "viewer");
  }

  public PlayerRef getViewer() {
    return viewer;
  }

  public GolemWindow setTag(String tag) {
    this.tag = tag;
    return this;
  }

  public String getTag() {
    return tag;
  }

  public GolemWindow setDecorator(Decorator decorator) {
    this.decorator = decorator;
    return this;
  }

  public GolemWindow setTitle(String title) {
    this.title = title == null ? "" : title;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public GolemWindow setViewportHeight(int rows) {
    viewportHeight = Math.max(1, Math.min(ChestMenu.MAX_ROWS, rows));
    return this;
  }

  public int getViewportHeight() {
    return viewportHeight;
  }

  /** Lets the viewer move items in their own inventory while the window is open (see {@link ChestMenu#freePlayerInventory()}). */
  public GolemWindow setFreePlayerInventory(boolean free) {
    this.freePlayerInventory = free;
    return this;
  }

  /** How native clients arrange the window (see {@link ChestMenu#layout()}); null lets them infer it. */
  public GolemWindow setLayout(MenuLayout layout) {
    this.layout = layout;
    return this;
  }

  /** The page shown, for native page controls; null for a single page. */
  public GolemWindow setPaging(MenuPaging paging) {
    this.paging = paging;
    return this;
  }

  /** Names of the windows above this one, outermost first, for native title bars. */
  public GolemWindow setBreadcrumb(List<String> breadcrumb) {
    this.breadcrumb = breadcrumb == null ? List.of() : List.copyOf(breadcrumb);
    return this;
  }

  public GolemWindow setElement(int position, int row, GolemElement element) {
    int key = key(position, row);
    if (key < 0) {
      return this;
    }
    if (element == null) {
      elements.remove(key);
    } else {
      elements.put(key, element);
    }
    return this;
  }

  /** Replaces the element and re-renders the open window; null clears the slot back to its background. */
  public GolemWindow updateElement(int position, int row, GolemElement element) {
    setElement(position, row, element);
    update();
    return this;
  }

  public GolemElement getElement(int position, int row) {
    return elements.get(key(position, row));
  }

  public boolean hasElement(int position, int row) {
    return elements.containsKey(key(position, row));
  }

  public GolemWindow onClosed(Consumer<GolemWindow> callback) {
    this.onClosed = callback;
    return this;
  }

  public ChestMenu build() {
    ChestMenu.Builder builder = ChestMenu.builder(title, viewportHeight);
    for (int row = 0; row < viewportHeight; row++) {
      for (int position = -CENTER; position <= CENTER; position++) {
        GolemElement element = elements.get(key(position, row));
        if (element == null && decorator != null) {
          element = decorator.decorate(this, position, row);
        }
        if (element != null) {
          builder.set(ChestMenu.slot(row, position + CENTER), element.button());
        }
      }
    }
    builder.freePlayerInventory(freePlayerInventory);
    builder.layout(layout).paging(paging).breadcrumb(breadcrumb);
    builder.onClose((MenuSession closed) -> closed(closed));
    return builder.build();
  }

  public GolemWindow open() {
    ChestMenu menu = build();
    rendered = menu;
    session = AdaptApi.platform().menus().open(viewer, menu);
    return this;
  }

  /** Re-renders in place while this window is the viewer's open menu. */
  public GolemWindow update() {
    if (!isVisible()) {
      return this;
    }
    ChestMenu menu = build();
    rendered = menu;
    session.update(menu);
    return this;
  }

  /** True while the viewer still has exactly this window open. */
  public boolean isVisible() {
    MenuSession open = session;
    if (open == null || !open.isOpen() || open.menu() != rendered) {
      return false;
    }
    return AdaptApi.isRunning() && AdaptApi.platform().menus().current(viewer) == open;
  }

  public void close() {
    if (isVisible()) {
      session.close();
    }
  }

  private void closed(MenuSession closed) {
    if (closed != session) {
      return;
    }
    if (closed.menu() != rendered) {
      return;
    }
    Consumer<GolemWindow> callback = onClosed;
    if (callback != null) {
      callback.accept(this);
    }
  }

  private static int key(int position, int row) {
    if (position < -CENTER || position > CENTER || row < 0 || row >= ChestMenu.MAX_ROWS) {
      return -1;
    }
    return (row * GolemLayout.WIDTH) + position + CENTER;
  }

  @FunctionalInterface
  public interface Decorator {
    GolemElement decorate(GolemWindow window, int position, int row);
  }
}
