package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Wraps the platform {@link MenuHost}. Viewers whose client negotiated the panel capability get chest menus as
 * native panels; everyone else goes straight to the delegate. Storage windows always use the delegate, since they
 * hold real items. Inbound panel frames go to {@link #panels()}.
 */
public final class ClientAwareMenuHost implements MenuHost {
  private final MenuHost delegate;
  private final PanelSessions panels;

  public ClientAwareMenuHost(MenuHost delegate, PanelLink link) {
    this(delegate, link, ViewerExecutor.tasks());
  }

  public ClientAwareMenuHost(MenuHost delegate, PanelLink link, ViewerExecutor executor) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.panels = new PanelSessions(delegate, link, executor);
  }

  public PanelFrameHandler panels() {
    return panels;
  }

  @Override
  public MenuSession open(PlayerRef viewer, ChestMenu menu) {
    if (panels.capable(viewer)) {
      return panels.open(viewer, menu);
    }
    panels.release(viewer);
    return delegate.open(viewer, menu);
  }

  @Override
  public MenuSession current(PlayerRef viewer) {
    MenuSession panel = panels.current(viewer);
    return panel != null ? panel : delegate.current(viewer);
  }

  @Override
  public void closeAll() {
    panels.closeAll();
    delegate.closeAll();
  }

  @Override
  public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
    panels.closeNative(viewer);
    return delegate.openStorage(viewer, title, size, onClose);
  }

  @Override
  public StorageView currentStorage(PlayerRef viewer) {
    return delegate.currentStorage(viewer);
  }
}
