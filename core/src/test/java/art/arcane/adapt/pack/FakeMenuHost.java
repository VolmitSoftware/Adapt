package art.arcane.adapt.pack;

import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The platform chest host stand-in: records what it was asked to open. */
public final class FakeMenuHost implements HostMenus {
  public final List<ChestMenu> opened = new ArrayList<>();
  public final List<String> storage = new ArrayList<>();
  public boolean closedAll;
  private MenuSession current;

  @Override
  public MenuSession open(PlayerRef viewer, ChestMenu menu) {
    opened.add(menu);
    current = new MenuSession() {
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
        return true;
      }

      @Override
      public void update(ChestMenu next) {
      }

      @Override
      public void close() {
      }
    };
    return current;
  }

  @Override
  public MenuSession current(PlayerRef viewer) {
    return current;
  }

  @Override
  public void closeAll() {
    closedAll = true;
  }

  @Override
  public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
    storage.add(title);
    return null;
  }

  @Override
  public StorageView currentStorage(PlayerRef viewer) {
    return null;
  }
}
