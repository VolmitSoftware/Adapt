package art.arcane.adapt.pack.surface;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;

/**
 * The platform side of pack windows: a chest window with a font-run title, pack slot looks, cycling slots and,
 * where {@link #support} allows it, stand-in buttons in the player slots that never touch the real inventory.
 * <ul>
 *   <li>Clicks: slots below {@code menu.size()} reach the menu's buttons as today; {@code menu.size()} to
 *   {@code menu.size() + 35} reach {@code surface.lower()} (see {@link PackSurface#lowerIndex}). Click kinds map as
 *   for chest menus.</li>
 *   <li>Updates ({@link PackSession#update} and {@code MenuSession#update}) keep the window when the row count and the
 *   presence of lower buttons stay the same: a new title is resent in place where {@code retitleInPlace}, slots change
 *   in place. Anything else opens a new window and retires the old one without its close callback.</li>
 *   <li>A surface with lower buttons for a viewer without {@code lowerSlots}: the lower buttons are dropped.
 *   {@code ChestMenu.freePlayerInventory()} with lower buttons is refused with {@link IllegalArgumentException}.</li>
 *   <li>The menu's close callback runs once when the window closes for any reason, replacement by {@link #open}
 *   included, but not when an update reopens it; the real inventory is resent to the client on close.</li>
 *   <li>A button's click handler may open, update or close windows.</li>
 * </ul>
 * Call on the viewer's owning thread.
 */
public interface PackWindows {
  /** Opens the window, replacing any window the viewer has open. */
  PackSession open(PlayerRef viewer, ChestMenu menu, PackSurface surface);

  /** What windows opened for {@code viewer} can do; asked before every page is laid out. */
  PackSupport support(PlayerRef viewer);
}
