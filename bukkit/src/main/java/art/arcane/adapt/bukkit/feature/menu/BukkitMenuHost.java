package art.arcane.adapt.bukkit.feature.menu;

import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.volmlib.util.inventorygui.Window;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Adapt chest menus rendered through VolmLib inventorygui windows, like the plugin's own GUIs. */
public final class BukkitMenuHost implements HostMenus {
  private final JavaPlugin plugin;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
  private final AtomicBoolean storageListening = new AtomicBoolean();

  public BukkitMenuHost(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  @Override
  public MenuSession open(PlayerRef viewer, ChestMenu menu) {
    Player player = BukkitRefs.player(viewer);
    Session existing = sessions.get(player.getUniqueId());
    if (existing != null && existing.isOpen() && existing.menu.size() == menu.size() && existing.menu.title().equals(menu.title())) {
      existing.update(menu);
      return existing;
    }
    Session session = new Session(viewer, player.getUniqueId(), menu);
    session.openWindow(player);
    return session;
  }

  @Override
  public MenuSession current(PlayerRef viewer) {
    Session session = sessions.get(viewer.uuid());
    return session != null && session.isOpen() ? session : null;
  }

  @Override
  public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
    if (storageListening.compareAndSet(false, true)) {
      plugin.getServer().getPluginManager().registerEvents(new BukkitStorageView.CloseListener(), plugin);
    }
    Player player = BukkitRefs.player(viewer);
    BukkitStorageView view = new BukkitStorageView(viewer, title, size, onClose);
    return player.openInventory(view.getInventory()) == null ? null : view;
  }

  @Override
  public StorageView currentStorage(PlayerRef viewer) {
    Player player = BukkitRefs.player(viewer);
    if (player == null) {
      return null;
    }
    return PaperApi.holder(player.getOpenInventory().getTopInventory()) instanceof BukkitStorageView view ? view : null;
  }

  @Override
  public void closeAll() {
    for (Session session : new ArrayList<>(sessions.values())) {
      session.close();
    }
    sessions.clear();
  }

  /** Routes a click to the button in that slot of the session's current menu. Display-only and empty slots ignore it. */
  static void dispatch(MenuSession session, int slot, ClickKind kind) {
    MenuButton button = session.menu().button(slot);
    if (button == null || !button.clickable()) {
      return;
    }
    button.action().click(new MenuClick(session.viewer(), session, slot, kind));
  }

  private final class Session implements MenuSession {
    private final PlayerRef viewer;
    private final UUID owner;
    private ChestMenu menu;
    private MenuWindow window;

    private Session(PlayerRef viewer, UUID owner, ChestMenu menu) {
      this.viewer = viewer;
      this.owner = owner;
      this.menu = menu;
    }

    private void openWindow(Player player) {
      MenuWindow created = new MenuWindow(plugin, player, menu, this::clicked, BukkitText.legacy(Objects.requireNonNullElse(menu.title(), "")));
      created.onClosed((Window closed) -> closed(created));
      created.render(menu);
      window = created;
      sessions.put(owner, this);
      created.open();
      created.opened();
    }

    private void clicked(MenuWindow clicked, int slot, ClickKind kind) {
      if (clicked == window) {
        dispatch(this, slot, kind);
      }
    }

    private void closed(MenuWindow closed) {
      closed.closed();
      if (closed != window) {
        return;
      }
      window = null;
      finish();
    }

    private void finish() {
      sessions.remove(owner, this);
      Consumer<MenuSession> callback = menu.onClose();
      if (callback != null) {
        callback.accept(this);
      }
    }

    @Override
    public PlayerRef viewer() {
      return viewer;
    }

    @Override
    public ChestMenu menu() {
      return menu;
    }

    @Override
    public boolean isOpen() {
      return window != null && window.isVisible() && sessions.get(owner) == this;
    }

    @Override
    public void update(ChestMenu next) {
      boolean reopen = next.size() != menu.size() || !next.title().equals(menu.title());
      menu = next;
      if (!isOpen()) {
        return;
      }
      if (reopen) {
        MenuWindow previous = window;
        openWindow(previous.getViewer());
        return;
      }
      window.render(next);
    }

    @Override
    public void close() {
      if (!isOpen()) {
        return;
      }
      MenuWindow open = window;
      window = null;
      open.close();
      open.closed();
      finish();
    }
  }
}
