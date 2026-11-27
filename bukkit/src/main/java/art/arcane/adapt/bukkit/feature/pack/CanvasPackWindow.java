package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.canvas.CanvasInput;
import art.arcane.adapt.bukkit.canvas.CanvasItem;
import art.arcane.adapt.bukkit.canvas.CanvasWindow;
import art.arcane.adapt.bukkit.canvas.Canvases;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

import java.util.List;
import java.util.Map;

/** A pack window on a server canvas: chest rows plus the 36 player slots, retitled in place. */
final class CanvasPackWindow implements PackWindow {
  private volatile BukkitPackSession owner;
  private CanvasWindow handle;

  private CanvasPackWindow(BukkitPackSession owner) {
    this.owner = owner;
  }

  /** Opens the window, or returns null when the server refused it. */
  static CanvasPackWindow open(Canvases canvases, Player viewer, int rows, List<TitleRun> title,
                               Map<Integer, PackFrame.Shown> frame, BukkitPackSession owner) {
    CanvasItem[] items = new CanvasItem[(rows * ChestMenu.COLUMNS) + PackSlot.LOWER_SLOTS];
    for (Map.Entry<Integer, PackFrame.Shown> entry : frame.entrySet()) {
      if (entry.getKey() < items.length) {
        items[entry.getKey()] = PackItems.canvas(entry.getValue().icon(), entry.getValue().look());
      }
    }
    CanvasPackWindow window = new CanvasPackWindow(owner);
    CanvasWindow opened = canvases.open(viewer, rows, PackTitles.json(title), items, window::clicked, window::closed);
    if (opened == null) {
      return null;
    }
    window.handle = opened;
    return window;
  }

  private void clicked(int windowSlot, int button, CanvasInput input) {
    ClickKind kind = PackClicks.kind(button, input);
    if (kind != null && windowSlot >= 0) {
      owner.clicked(this, windowSlot, kind);
    }
  }

  private void closed() {
    owner.closed(this);
  }

  InventoryView view() {
    return handle.view();
  }

  @Override
  public boolean canvas() {
    return true;
  }

  @Override
  public int rows() {
    return handle.rows();
  }

  @Override
  public boolean isOpen() {
    return handle.isOpen();
  }

  @Override
  public void owner(BukkitPackSession session) {
    owner = session;
  }

  @Override
  public void set(int windowSlot, PackFrame.Shown shown) {
    handle.set(windowSlot, shown == null ? null : PackItems.canvas(shown.icon(), shown.look()));
  }

  @Override
  public void flush() {
    handle.flush();
  }

  @Override
  public boolean retitlesInPlace() {
    return true;
  }

  @Override
  public void retitle(List<TitleRun> title) {
    handle.retitle(PackTitles.json(title));
  }

  @Override
  public void retire(boolean restoreInventory) {
    handle.retire(restoreInventory);
  }

  @Override
  public void close() {
    handle.close();
  }
}
