package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.entity.PlayerRef;

/** An open menu for one viewer. Owning-thread only. */
public interface MenuSession {
  PlayerRef viewer();

  ChestMenu menu();

  boolean isOpen();

  /** Re-renders in place. A different row count or title reopens the window. */
  void update(ChestMenu menu);

  void close();
}
