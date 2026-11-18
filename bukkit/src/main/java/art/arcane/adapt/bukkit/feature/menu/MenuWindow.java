package art.arcane.adapt.bukkit.feature.menu;

import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An inventorygui window that also reports number-key, drop, double-click and offhand-swap clicks, which
 * the base window swallows. Slots map to window coordinates as {@code position = column - 4}.
 */
final class MenuWindow extends UIWindow {
  interface Clicks {
    void clicked(MenuWindow window, int slot, ClickKind kind);
  }

  private static final int CENTER_OFFSET = ChestMenu.COLUMNS / 2;
  private static final Set<InventoryAction> FREE_INVENTORY_ACTIONS = EnumSet.of(
      InventoryAction.PICKUP_ALL,
      InventoryAction.PICKUP_SOME,
      InventoryAction.PICKUP_HALF,
      InventoryAction.PICKUP_ONE,
      InventoryAction.PLACE_ALL,
      InventoryAction.PLACE_SOME,
      InventoryAction.PLACE_ONE,
      InventoryAction.SWAP_WITH_CURSOR);
  private static final Map<UUID, MenuWindow> OPEN = new ConcurrentHashMap<>();
  private final Clicks clicks;
  private boolean freePlayerInventory;

  MenuWindow(JavaPlugin plugin, Player viewer, ChestMenu menu, Clicks clicks, String legacyTitle) {
    super(plugin, viewer);
    this.clicks = clicks;
    setViewportHeight(menu.rows());
    setTitle(legacyTitle);
  }

  void render(ChestMenu menu) {
    freePlayerInventory = menu.freePlayerInventory();
    batch(() -> {
      clearElements();
      for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
        int slot = entry.getKey();
        setElement(position(slot), row(slot), new IconElement(this, slot, MenuIcons.stack(entry.getValue().icon())));
      }
    });
  }

  static boolean allowsFreeInventoryAction(InventoryAction action) {
    return FREE_INVENTORY_ACTIONS.contains(action);
  }

  void opened() {
    OPEN.put(getViewer().getUniqueId(), this);
  }

  void closed() {
    OPEN.remove(getViewer().getUniqueId(), this);
  }

  static void forget(UUID viewer) {
    OPEN.remove(viewer);
  }

  /** True when the viewer's open menu lets them move items in their own inventory. */
  static boolean freeInventoryOpen(UUID viewer) {
    MenuWindow window = OPEN.get(viewer);
    return window != null && window.freePlayerInventory && window.isVisible();
  }

  static int position(int slot) {
    return (slot % ChestMenu.COLUMNS) - CENTER_OFFSET;
  }

  static int row(int slot) {
    return slot / ChestMenu.COLUMNS;
  }

  @Override
  @EventHandler
  public void on(InventoryClickEvent event) {
    boolean wasCancelled = event.isCancelled();
    super.on(event);
    if (wasCancelled || !event.isCancelled()) {
      return;
    }
    ClickKind kind = MenuClicks.extraKind(event.getClick());
    if (kind == null) {
      return;
    }
    Inventory top = inventoryViewTopInventory(event.getView());
    int raw = event.getRawSlot();
    if (top == null || raw < 0 || raw >= top.getSize()) {
      return;
    }
    clicks.clicked(this, (getViewportPosition() * ChestMenu.COLUMNS) + raw, kind);
  }

  private static final class IconElement extends UIElement {
    private final ItemStack stack;

    private IconElement(MenuWindow window, int slot, ItemStack stack) {
      super("adapt-menu-" + slot);
      this.stack = stack;
      onLeftClick((Element element) -> window.clicks.clicked(window, slot, ClickKind.LEFT));
      onRightClick((Element element) -> window.clicks.clicked(window, slot, ClickKind.RIGHT));
      onMiddleClick((Element element) -> window.clicks.clicked(window, slot, ClickKind.MIDDLE));
      onShiftLeftClick((Element element) -> window.clicks.clicked(window, slot, ClickKind.SHIFT_LEFT));
      onShiftRightClick((Element element) -> window.clicks.clicked(window, slot, ClickKind.SHIFT_RIGHT));
    }

    @Override
    public ItemStack computeItemStack() {
      return stack.clone();
    }
  }
}
