package art.arcane.adapt.platform.menu;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Immutable chest menu: title, 1 to 6 rows of 9 slots, buttons by slot and an optional close
 * callback. Rendered by {@link MenuHost}; Bukkit renders it through inventorygui-style chest windows,
 * modded through a vanilla chest container. {@code layout}, {@code paging} and {@code breadcrumb} only describe the
 * menu to clients that draw it natively; chests ignore them.
 */
public final class ChestMenu {
  public static final int COLUMNS = 9;
  public static final int MAX_ROWS = 6;

  private final String title;
  private final int rows;
  private final Map<Integer, MenuButton> buttons;
  private final Consumer<MenuSession> onClose;
  private final boolean freePlayerInventory;
  private final MenuLayout layout;
  private final MenuPaging paging;
  private final List<String> breadcrumb;

  private ChestMenu(Builder builder) {
    this.title = builder.title;
    this.rows = builder.rows;
    this.buttons = Collections.unmodifiableMap(new TreeMap<>(builder.buttons));
    this.onClose = builder.onClose;
    this.freePlayerInventory = builder.freePlayerInventory;
    this.layout = builder.layout;
    this.paging = builder.paging;
    this.breadcrumb = builder.breadcrumb;
  }

  public static Builder builder(String title, int rows) {
    return new Builder(title, rows);
  }

  public static int slot(int row, int column) {
    if (row < 0 || row >= MAX_ROWS || column < 0 || column >= COLUMNS) {
      throw new IllegalArgumentException("row/column out of range: " + row + "," + column);
    }
    return (row * COLUMNS) + column;
  }

  public String title() {
    return title;
  }

  public int rows() {
    return rows;
  }

  public int size() {
    return rows * COLUMNS;
  }

  /** Buttons by slot, in slot order. */
  public Map<Integer, MenuButton> buttons() {
    return buttons;
  }

  public MenuButton button(int slot) {
    return buttons.get(slot);
  }

  public Consumer<MenuSession> onClose() {
    return onClose;
  }

  /**
   * True when the viewer may pick up, place and swap items in their own inventory rows while the menu is open,
   * so an item can be carried on the cursor onto a menu button (read it with {@code PlayerInventoryRef#cursor()}).
   * Shift-clicks and drags stay refused, and the menu's own slots never move.
   */
  public boolean freePlayerInventory() {
    return freePlayerInventory;
  }

  /** How native clients arrange the menu, or null to let them infer it. */
  public MenuLayout layout() {
    return layout;
  }

  /** The page this menu shows, or null when it has one page. */
  public MenuPaging paging() {
    return paging;
  }

  /** Names of the menus above this one, outermost first. */
  public List<String> breadcrumb() {
    return breadcrumb;
  }

  public Builder toBuilder() {
    Builder builder = new Builder(title, rows);
    builder.buttons.putAll(buttons);
    builder.onClose = onClose;
    builder.freePlayerInventory = freePlayerInventory;
    builder.layout = layout;
    builder.paging = paging;
    builder.breadcrumb = breadcrumb;
    return builder;
  }

  public static final class Builder {
    private final String title;
    private final int rows;
    private final TreeMap<Integer, MenuButton> buttons = new TreeMap<>();
    private Consumer<MenuSession> onClose;
    private boolean freePlayerInventory;
    private MenuLayout layout;
    private MenuPaging paging;
    private List<String> breadcrumb = List.of();

    private Builder(String title, int rows) {
      this.title = Objects.requireNonNull(title, "title");
      if (rows < 1 || rows > MAX_ROWS) {
        throw new IllegalArgumentException("rows must be 1.." + MAX_ROWS + ": " + rows);
      }
      this.rows = rows;
    }

    public Builder set(int slot, MenuButton button) {
      if (slot < 0 || slot >= rows * COLUMNS) {
        throw new IllegalArgumentException("slot " + slot + " outside 0.." + ((rows * COLUMNS) - 1));
      }
      buttons.put(slot, Objects.requireNonNull(button, "button"));
      return this;
    }

    public Builder set(int row, int column, MenuButton button) {
      return set(slot(row, column), button);
    }

    public Builder clear(int slot) {
      buttons.remove(slot);
      return this;
    }

    /** Fills every empty slot with the button. */
    public Builder fill(MenuButton button) {
      for (int slot = 0; slot < rows * COLUMNS; slot++) {
        buttons.putIfAbsent(slot, button);
      }
      return this;
    }

    public Builder onClose(Consumer<MenuSession> callback) {
      this.onClose = callback;
      return this;
    }

    public Builder freePlayerInventory(boolean free) {
      this.freePlayerInventory = free;
      return this;
    }

    public Builder layout(MenuLayout newLayout) {
      this.layout = newLayout;
      return this;
    }

    public Builder paging(MenuPaging newPaging) {
      this.paging = newPaging;
      return this;
    }

    public Builder breadcrumb(List<String> crumbs) {
      this.breadcrumb = crumbs == null ? List.of() : List.copyOf(crumbs);
      return this;
    }

    public ChestMenu build() {
      return new ChestMenu(this);
    }
  }
}
