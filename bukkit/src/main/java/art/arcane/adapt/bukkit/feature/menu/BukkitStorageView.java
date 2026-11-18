package art.arcane.adapt.bukkit.feature.menu;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.entity.BukkitInventoryRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.StorageView;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A plain Bukkit inventory owned by this holder; its close is reported once through the callback. */
final class BukkitStorageView implements StorageView, InventoryHolder {
  private final PlayerRef viewer;
  private final Inventory inventory;
  private final Consumer<StorageView> onClose;
  private final AtomicBoolean closed = new AtomicBoolean();

  BukkitStorageView(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
    this.viewer = Objects.requireNonNull(viewer, "viewer");
    this.onClose = onClose;
    this.inventory = size == 9
        ? Bukkit.createInventory(this, InventoryType.DISPENSER, title)
        : Bukkit.createInventory(this, size, title);
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  @Override
  public PlayerRef viewer() {
    return viewer;
  }

  @Override
  public InventoryRef inventory() {
    return new BukkitInventoryRef(inventory);
  }

  @Override
  public int size() {
    return inventory.getSize();
  }

  @Override
  public boolean isOpen() {
    Player player = BukkitRefs.player(viewer);
    return player != null && player.getOpenInventory().getTopInventory() == inventory;
  }

  @Override
  public void close() {
    Player player = BukkitRefs.player(viewer);
    if (isOpen()) {
      player.closeInventory();
    }
  }

  void closed() {
    if (closed.compareAndSet(false, true) && onClose != null) {
      onClose.accept(this);
    }
  }

  static final class CloseListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryCloseEvent event) {
      if (event.getInventory().getHolder(false) instanceof BukkitStorageView view) {
        view.closed();
      }
    }
  }
}
