package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import java.util.List;
import java.util.Map;

/**
 * A pack window through the Bukkit API alone: chest rows over the live player inventory, a component title on Paper
 * (plain text on Spigot), a new window for every new title. Clicks arrive through {@link PackWindowListener}.
 */
final class ChestPackWindow implements PackWindow, InventoryHolder {
  private final Player viewer;
  private final boolean freePlayerInventory;
  private final Inventory inventory;
  private volatile BukkitPackSession owner;
  private boolean retired;

  private ChestPackWindow(Player viewer, ChestMenu menu, List<TitleRun> title, BukkitPackSession owner) {
    this.viewer = viewer;
    this.freePlayerInventory = menu.freePlayerInventory();
    this.owner = owner;
    this.inventory = PaperApi.isPaper()
        ? PaperPackText.chest(this, menu.size(), PackTitles.component(title))
        : Bukkit.createInventory(this, menu.size(), PackTitles.plain(title));
  }

  /** Opens the window, or returns null when the open was refused. */
  static ChestPackWindow open(Player viewer, ChestMenu menu, List<TitleRun> title, Map<Integer, PackFrame.Shown> frame,
                              BukkitPackSession owner) {
    ChestPackWindow window = new ChestPackWindow(viewer, menu, title, owner);
    for (Map.Entry<Integer, PackFrame.Shown> entry : frame.entrySet()) {
      if (entry.getKey() < menu.size()) {
        window.set(entry.getKey(), entry.getValue());
      }
    }
    InventoryView view = viewer.openInventory(window.inventory);
    return view == null || !window.isOpen() ? null : window;
  }

  /** True when this window belongs to {@code windows}, so each installed listener handles its own windows only. */
  boolean ownedBy(BukkitPackWindows windows) {
    return owner.windows() == windows;
  }

  boolean freePlayerInventory() {
    return freePlayerInventory;
  }

  void clicked(int windowSlot, ClickKind kind) {
    if (!retired) {
      owner.clicked(this, windowSlot, kind);
    }
  }

  void closedByViewer() {
    if (!retired) {
      retired = true;
      owner.closed(this);
    }
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  @Override
  public boolean canvas() {
    return false;
  }

  @Override
  public int rows() {
    return inventory.getSize() / ChestMenu.COLUMNS;
  }

  @Override
  public boolean isOpen() {
    return !retired && showing();
  }

  private boolean showing() {
    return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == this;
  }

  @Override
  public void owner(BukkitPackSession session) {
    owner = session;
  }

  @Override
  public void set(int windowSlot, PackFrame.Shown shown) {
    if (windowSlot < inventory.getSize()) {
      inventory.setItem(windowSlot, shown == null ? null : PackItems.chest(shown.icon(), shown.look()));
    }
  }

  @Override
  public void flush() {
  }

  @Override
  public boolean retitlesInPlace() {
    return false;
  }

  @Override
  public void retitle(List<TitleRun> title) {
    throw new UnsupportedOperationException("chest pack windows reopen for a new title");
  }

  @Override
  public void retire(boolean restoreInventory) {
    retired = true;
  }

  @Override
  public void close() {
    if (showing()) {
      viewer.closeInventory();
    }
  }
}
