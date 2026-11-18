package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * A plain container window Adapt opens for one viewer. Unlike {@link ChestMenu}s, players move items in and out of
 * it freely and its clicks and drags are posted as ordinary inventory events. Owning-thread only.
 */
public interface StorageView {
  PlayerRef viewer();

  /** The window's own slots, raw slots {@code 0..size()-1} of its click events. */
  InventoryRef inventory();

  int size();

  /** True while this window is the viewer's open container. */
  boolean isOpen();

  void close();
}
