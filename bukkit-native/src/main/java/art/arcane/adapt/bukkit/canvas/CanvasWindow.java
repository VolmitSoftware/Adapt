package art.arcane.adapt.bukkit.canvas;

import org.bukkit.inventory.InventoryView;

/**
 * An open canvas: a chest window of {@link #rows()} rows whose 36 player slots show stand-in items from a private
 * container. The viewer's real inventory is never touched and is resent to the client when the window closes.
 * Owning thread only.
 */
public interface CanvasWindow {
  int rows();

  /** The Bukkit view click events carry for this window. */
  InventoryView view();

  /** True while this window is the viewer's open container. */
  boolean isOpen();

  /** Sets window slot {@code windowSlot} (chest slots first, then the 36 player slots); null empties it. */
  void set(int windowSlot, CanvasItem item);

  /** Sends the slots changed since the last flush. */
  void flush();

  /** Changes the title in place: same container, cursor, carried stack and hovered slot. Resends every slot. */
  void retitle(String titleJson);

  /**
   * Gives up the window to a new one without running the close callback. {@code restoreInventory} resends the real
   * inventory and lifts the packet guard, for when the next window is not a canvas.
   */
  void retire(boolean restoreInventory);

  /** Closes the window; the close callback runs unless the window was retired. */
  void close();
}
