package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.function.Consumer;

/** Opens chest menus. Menu clicks never reach {@code InventoryClickEvent} listeners. */
public interface MenuHost {
  /** Opens the menu, replacing any menu the viewer has open. Call on the viewer's owning thread. */
  MenuSession open(PlayerRef viewer, ChestMenu menu);

  /** The viewer's open Adapt menu, or null. */
  MenuSession current(PlayerRef viewer);

  void closeAll();

  /**
   * Opens an empty storage window of {@code size} slots (a multiple of 9 up to 54; 9 opens a 3x3 dispenser-style
   * window). {@code onClose} runs once when the window closes for any reason. Returns null when the platform refused
   * to open it. Call on the viewer's owning thread.
   */
  StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose);

  /** The viewer's open storage window, or null when their open container is anything else. */
  StorageView currentStorage(PlayerRef viewer);
}
