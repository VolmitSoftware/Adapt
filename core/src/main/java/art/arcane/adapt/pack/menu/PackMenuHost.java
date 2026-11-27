package art.arcane.adapt.pack.menu;

import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.link.PackAudience;
import art.arcane.adapt.pack.surface.PackSession;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.PackWindows;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sits between {@code ClientAwareMenuHost} and the platform host. Viewers with the pack loaded get every chest menu
 * through {@link PackWindows} in the {@link LedgerSkin}; pack screens open their own pages through
 * {@link #openPage}. Everyone else, and every storage window, goes to the delegate unchanged. Asks the windows'
 * {@link art.arcane.adapt.pack.surface.PackSupport} before every page is laid out.
 */
public final class PackMenuHost implements HostMenus {
  private final HostMenus delegate;
  private final PackWindows windows;
  private final PackAudience audience;
  private final Supplier<PackResources> resources;
  private final Map<UUID, Open> open = new ConcurrentHashMap<>();

  public PackMenuHost(HostMenus delegate, PackWindows windows, PackAudience audience, Supplier<PackResources> resources) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.windows = Objects.requireNonNull(windows, "windows");
    this.audience = Objects.requireNonNull(audience, "audience");
    this.resources = Objects.requireNonNull(resources, "resources");
  }

  /** True when {@code viewer} sees book menus now: the pack is loaded for them and the renderer has its resources. */
  public boolean book(PlayerRef viewer) {
    return viewer != null && audience.book(viewer) && resources.get() != null;
  }

  /** The resources pages are composed with, or null while the pack is not ready. */
  public PackResources resources() {
    return resources.get();
  }

  /** True when a window for {@code viewer} can take over the player slots. */
  public boolean lowerSlots(PlayerRef viewer) {
    return windows.support(viewer).lowerSlots();
  }

  @Override
  public MenuSession open(PlayerRef viewer, ChestMenu menu) {
    PackResources loaded = resources.get();
    if (viewer == null || loaded == null || !audience.book(viewer)) {
      open.remove(key(viewer));
      return delegate.open(viewer, menu);
    }
    SkinnedSession session = new SkinnedSession(viewer, menu);
    LedgerSkin.Skinned skinned = LedgerSkin.skin(menu, loaded, session, closer(viewer, menu));
    session.window = track(viewer, session, windows.open(viewer, skinned.menu(), skinned.surface()));
    return session;
  }

  /**
   * Opens a pack page as it is, replacing whatever the viewer has open. The menu's close callback receives the
   * returned session.
   */
  public PackSession openPage(PlayerRef viewer, ChestMenu menu, PackSurface surface) {
    PackSession session = windows.open(viewer, tracked(viewer, menu), surface);
    track(viewer, session, session);
    return session;
  }

  /** Shows a pack page in the viewer's open pack window in place; false when none is open. */
  public boolean updatePage(PlayerRef viewer, ChestMenu menu, PackSurface surface) {
    PackSession session = window(viewer);
    if (session == null) {
      return false;
    }
    track(viewer, session, session);
    session.update(tracked(viewer, menu), surface);
    return true;
  }

  private ChestMenu tracked(PlayerRef viewer, ChestMenu menu) {
    Consumer<MenuSession> onClose = menu.onClose();
    return menu.toBuilder().onClose((MenuSession session) -> {
      untrack(viewer, session);
      if (onClose != null) {
        onClose.accept(session);
      }
    }).build();
  }

  /** The viewer's open pack page or skinned menu window, or null. */
  public PackSession window(PlayerRef viewer) {
    Open current = open.get(key(viewer));
    return current == null || !current.window().isOpen() ? null : current.window();
  }

  @Override
  public MenuSession current(PlayerRef viewer) {
    Open current = open.get(key(viewer));
    if (current != null && current.window().isOpen()) {
      return current.exposed();
    }
    return delegate.current(viewer);
  }

  @Override
  public void closeAll() {
    List<Open> sessions = new ArrayList<>(open.values());
    open.clear();
    for (Open session : sessions) {
      if (session.window().isOpen()) {
        session.window().close();
      }
    }
    delegate.closeAll();
  }

  @Override
  public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
    open.remove(key(viewer));
    return delegate.openStorage(viewer, title, size, onClose);
  }

  @Override
  public StorageView currentStorage(PlayerRef viewer) {
    return delegate.currentStorage(viewer);
  }

  private PackSession track(PlayerRef viewer, MenuSession exposed, PackSession window) {
    open.put(key(viewer), new Open(exposed, window));
    return window;
  }

  private void untrack(PlayerRef viewer, MenuSession closing) {
    open.computeIfPresent(key(viewer), (UUID id, Open current) ->
        current.exposed() == closing || current.window() == closing ? null : current);
  }

  private Consumer<MenuSession> closer(PlayerRef viewer, ChestMenu menu) {
    return (MenuSession exposed) -> {
      untrack(viewer, exposed);
      if (menu.onClose() != null) {
        menu.onClose().accept(exposed);
      }
    };
  }

  private static UUID key(PlayerRef viewer) {
    return viewer == null ? new UUID(0L, 0L) : viewer.uuid();
  }

  private record Open(MenuSession exposed, PackSession window) {
  }

  /** The session callers of {@link #open} hold: updates are skinned again before they reach the window. */
  private final class SkinnedSession implements MenuSession {
    private final PlayerRef viewer;
    private ChestMenu menu;
    private PackSession window;

    private SkinnedSession(PlayerRef viewer, ChestMenu menu) {
      this.viewer = viewer;
      this.menu = menu;
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
      return window != null && window.isOpen();
    }

    @Override
    public void update(ChestMenu next) {
      PackResources loaded = resources.get();
      if (!isOpen() || loaded == null) {
        return;
      }
      menu = next;
      LedgerSkin.Skinned skinned = LedgerSkin.skin(next, loaded, this, closer(viewer, next));
      window.update(skinned.menu(), skinned.surface());
    }

    @Override
    public void close() {
      if (window != null) {
        window.close();
      }
    }
  }
}
