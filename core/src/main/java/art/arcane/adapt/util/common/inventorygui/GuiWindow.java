package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Chest menu builder with the coordinates of the inventorygui windows the Bukkit menus used: {@code position} runs
 * from -4 (left column) to 4 (right column), rows count from the top. Empty slots are filled by the decorator, the
 * viewport height is the chest row count. {@link #open()} renders it through the platform {@link MenuHost};
 * {@link #update()} re-renders an open window in place.
 */
public final class GuiWindow {
  private static final int CENTER = GuiLayout.WIDTH / 2;

  private final PlayerRef viewer;
  private final Map<Integer, GuiElement> elements = new HashMap<>();
  private Decorator decorator;
  private String tag;
  private String title = "";
  private int viewportHeight = GuiLayout.MAX_ROWS;
  private boolean freePlayerInventory;
  private Consumer<GuiWindow> onClosed;
  private MenuSession session;
  private ChestMenu rendered;

  public GuiWindow(PlayerRef viewer) {
    this.viewer = Objects.requireNonNull(viewer, "viewer");
  }

  public PlayerRef getViewer() {
    return viewer;
  }

  public GuiWindow setTag(String tag) {
    this.tag = tag;
    return this;
  }

  public String getTag() {
    return tag;
  }

  public GuiWindow setDecorator(Decorator decorator) {
    this.decorator = decorator;
    return this;
  }

  public GuiWindow setTitle(String title) {
    this.title = title == null ? "" : title;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public GuiWindow setViewportHeight(int rows) {
    viewportHeight = Math.max(1, Math.min(ChestMenu.MAX_ROWS, rows));
    return this;
  }

  public int getViewportHeight() {
    return viewportHeight;
  }

  /** Lets the viewer move items in their own inventory while the window is open (see {@link ChestMenu#freePlayerInventory()}). */
  public GuiWindow setFreePlayerInventory(boolean free) {
    this.freePlayerInventory = free;
    return this;
  }

  public GuiWindow setElement(int position, int row, GuiElement element) {
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
  public GuiWindow updateElement(int position, int row, GuiElement element) {
    setElement(position, row, element);
    update();
    return this;
  }

  public GuiElement getElement(int position, int row) {
    return elements.get(key(position, row));
  }

  public boolean hasElement(int position, int row) {
    return elements.containsKey(key(position, row));
  }

  public GuiWindow onClosed(Consumer<GuiWindow> callback) {
    this.onClosed = callback;
    return this;
  }

  public ChestMenu build() {
    ChestMenu.Builder builder = ChestMenu.builder(title, viewportHeight);
    for (int row = 0; row < viewportHeight; row++) {
      for (int position = -CENTER; position <= CENTER; position++) {
        GuiElement element = elements.get(key(position, row));
        if (element == null && decorator != null) {
          element = decorator.decorate(this, position, row);
        }
        if (element != null) {
          builder.set(ChestMenu.slot(row, position + CENTER), element.button());
        }
      }
    }
    builder.freePlayerInventory(freePlayerInventory);
    builder.onClose((MenuSession closed) -> closed(closed));
    return builder.build();
  }

  public GuiWindow open() {
    ChestMenu menu = build();
    rendered = menu;
    session = AdaptCore.platform().menus().open(viewer, menu);
    GuiSessions.track(this);
    return this;
  }

  /** Re-renders in place while this window is the viewer's open menu. */
  public GuiWindow update() {
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
    AdaptCore core = AdaptCore.current();
    return core != null && core.getPlatform().menus().current(viewer) == open;
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
    GuiSessions.untrack(viewer.uuid(), this);
    if (closed.menu() != rendered) {
      return;
    }
    Consumer<GuiWindow> callback = onClosed;
    if (callback != null) {
      callback.accept(this);
    }
  }

  private static int key(int position, int row) {
    if (position < -CENTER || position > CENTER || row < 0 || row >= ChestMenu.MAX_ROWS) {
      return -1;
    }
    return (row * GuiLayout.WIDTH) + position + CENTER;
  }

  @FunctionalInterface
  public interface Decorator {
    GuiElement decorate(GuiWindow window, int position, int row);
  }
}
