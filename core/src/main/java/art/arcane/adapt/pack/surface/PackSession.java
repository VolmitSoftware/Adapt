package art.arcane.adapt.pack.surface;

import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuSession;

/** An open pack window. Owning-thread only. */
public interface PackSession extends MenuSession {
  /** Shows the new menu and surface; {@link PackWindows} says when the window is kept and when it is replaced. */
  void update(ChestMenu menu, PackSurface surface);

  /** The surface on show. */
  PackSurface surface();

  /**
   * Starts a one-shot effect of {@code ticks} on the item in {@code slot}, through the cooldown clock of its look's
   * cooldown group; a window that cannot run one swaps nothing and the slot keeps its look.
   */
  void oneShot(PackSlot slot, int ticks);

  /** True when this window shows the surface's lower buttons in the player slots. */
  boolean lowerSlots();
}
