package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Native panel sessions, one per viewer. A session lives on the client as a panel until the viewer's client
 * withdraws, then continues as a chest menu of the delegate host under the same {@link MenuSession}, so the menu
 * that opened it keeps working. Close callbacks follow the chest hosts: they run once, with this session, when the
 * viewer closes the panel, another menu or storage window replaces it, it is closed in code, or the viewer quits.
 */
public final class PanelSessions implements PanelFrameHandler {
  private final MenuHost delegate;
  private final PanelLink link;
  private final ViewerExecutor executor;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
  private final AtomicInteger ids = new AtomicInteger();

  PanelSessions(MenuHost delegate, PanelLink link, ViewerExecutor executor) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.link = Objects.requireNonNull(link, "link");
    this.executor = Objects.requireNonNull(executor, "executor");
  }

  boolean capable(PlayerRef viewer) {
    return link.capable(viewer.uuid());
  }

  /** Opens the menu as a native panel, or as a chest when the client cannot take the frame. */
  MenuSession open(PlayerRef viewer, ChestMenu menu) {
    UUID owner = viewer.uuid();
    Session existing = sessions.get(owner);
    if (existing != null && existing.isOpen() && existing.chest == null && existing.menu.rows() == menu.rows()
        && existing.menu.title().equals(menu.title())) {
      existing.update(menu);
      return existing;
    }
    Session session = new Session(viewer, nextId(), menu);
    sessions.put(owner, session);
    MenuSession chest = existing != null && existing.chest != null ? existing.chest : delegate.current(viewer);
    if (chest != null && chest.isOpen()) {
      chest.close();
    }
    if (sessions.get(owner) != session) {
      return session;
    }
    if (!session.show()) {
      sessions.remove(owner, session);
      if (existing != null) {
        existing.finish(false);
      }
      return delegate.open(viewer, menu);
    }
    if (existing != null) {
      existing.finish(false);
    }
    return session;
  }

  /** The viewer's panel session, native or continued as a chest, or null. */
  MenuSession current(PlayerRef viewer) {
    if (sessions.isEmpty()) {
      return null;
    }
    Session session = sessions.get(viewer.uuid());
    return session != null && session.isOpen() ? session : null;
  }

  /** Forgets a session continued as a chest; the delegate is about to replace that chest. */
  void release(PlayerRef viewer) {
    if (sessions.isEmpty()) {
      return;
    }
    Session session = sessions.get(viewer.uuid());
    if (session != null && session.chest != null) {
      sessions.remove(viewer.uuid(), session);
    }
  }

  /** Closes a native panel before another window takes the viewer's screen. */
  void closeNative(PlayerRef viewer) {
    if (sessions.isEmpty()) {
      return;
    }
    Session session = sessions.get(viewer.uuid());
    if (session != null && session.chest == null) {
      session.close();
    }
  }

  void closeAll() {
    for (Session session : new ArrayList<>(sessions.values())) {
      session.close();
    }
    sessions.clear();
  }

  @Override
  public void click(UUID viewer, ClientFrame.PanelClick click) {
    onOwner(viewer, click.sessionId(), (Session session) -> {
      if (session.stale(click.revision())) {
        return;
      }
      MenuButton button = session.menu.button(click.slot());
      if (button == null || !button.clickable()) {
        return;
      }
      button.action().click(new MenuClick(session.viewer, session, click.slot(), click.click()));
    });
  }

  @Override
  public void input(UUID viewer, ClientFrame.PanelInput input) {
    onOwner(viewer, input.sessionId(), (Session session) -> {
      if (session.stale(input.revision())) {
        return;
      }
      MenuButton button = session.menu.button(input.slot());
      MenuHint hint = button == null ? null : button.hint();
      if (hint == null || hint.input() == null) {
        return;
      }
      hint.input().input(new MenuClick(session.viewer, session, input.slot(), ClickKind.OTHER), input.text());
    });
  }

  @Override
  public void closed(UUID viewer, ClientFrame.PanelClosed closed) {
    onOwner(viewer, closed.sessionId(), (Session session) -> session.finish(false));
  }

  @Override
  public void quit(UUID viewer) {
    Session session = sessions.get(viewer);
    if (session != null && session.chest == null) {
      session.finish(false);
    }
  }

  @Override
  public void withdrawn(UUID viewer) {
    Session session = sessions.get(viewer);
    if (session == null || session.chest != null) {
      return;
    }
    executor.execute(session.viewer, () -> {
      if (sessions.get(viewer) != session || session.closed || session.chest != null) {
        return;
      }
      if (!session.viewer.isOnline()) {
        session.finish(false);
        return;
      }
      session.continueAsChest();
    }, () -> quit(viewer));
  }

  private void onOwner(UUID viewer, int sessionId, Consumer<Session> task) {
    Session session = sessions.get(viewer);
    if (session == null || session.id != sessionId || session.chest != null) {
      return;
    }
    executor.execute(session.viewer, () -> {
      if (sessions.get(viewer) == session && !session.closed && session.chest == null) {
        task.accept(session);
      }
    }, null);
  }

  private int nextId() {
    return ids.updateAndGet((int current) -> current == Integer.MAX_VALUE ? 1 : current + 1);
  }

  private final class Session implements MenuSession {
    private final PlayerRef viewer;
    private final UUID owner;
    private final int id;
    private ChestMenu menu;
    private PanelView shown;
    private MenuSession chest;
    private boolean closed;

    private Session(PlayerRef viewer, int id, ChestMenu menu) {
      this.viewer = viewer;
      this.owner = viewer.uuid();
      this.id = id;
      this.menu = menu;
    }

    private boolean show() {
      shown = PanelViews.view(id, 0, menu);
      return link.send(owner, new ServerFrame.PanelOpen(shown));
    }

    /** True for a click against an older layout; the client gets the current one again. */
    private boolean stale(int revision) {
      if (revision == shown.revision()) {
        return false;
      }
      link.send(owner, new ServerFrame.PanelUpdate(shown));
      return true;
    }

    private void continueAsChest() {
      chest = delegate.open(viewer, chestMenu(menu));
    }

    private ChestMenu chestMenu(ChestMenu source) {
      return source.toBuilder().onClose((MenuSession closedChest) -> {
        if (closedChest == chest) {
          finish(false);
        }
      }).build();
    }

    private void finish(boolean notifyClient) {
      if (closed) {
        return;
      }
      closed = true;
      sessions.remove(owner, this);
      if (notifyClient) {
        link.send(owner, new ServerFrame.PanelClose(id));
      }
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
      return !closed && sessions.get(owner) == this && (chest == null || chest.isOpen());
    }

    @Override
    public void update(ChestMenu next) {
      menu = next;
      if (!isOpen()) {
        return;
      }
      if (chest != null) {
        chest.update(chestMenu(next));
        return;
      }
      PanelView content = PanelViews.view(id, shown.revision(), next);
      if (content.equals(shown)) {
        return;
      }
      shown = content.withRevision(shown.revision() + 1);
      link.send(owner, new ServerFrame.PanelUpdate(shown));
    }

    @Override
    public void close() {
      if (!isOpen()) {
        return;
      }
      if (chest != null) {
        chest.close();
        return;
      }
      finish(true);
    }
  }
}
