package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** An open menu for one viewer. Owning-thread only. */
@ApiStatus.AvailableSince("1.0.0")
public interface MenuSession {
  /** The player viewing the menu. */
  PlayerRef viewer();

  /** The menu currently shown. */
  ChestMenu menu();

  /** True while this session's window is the viewer's open Adapt menu. */
  boolean isOpen();

  /** Re-renders in place. A different row count or title reopens the window. */
  void update(ChestMenu menu);

  /** Closes the window and runs the menu's close callback; nothing happens when it is already closed. */
  void close();
}
