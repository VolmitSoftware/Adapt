package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Slot content. A null action makes the slot display-only. Clicks never move the icon item. {@code hint} may be
 * null; see {@link MenuHint}. {@code meta} says how native clients present the button; see {@link MenuMeta}.
 *
 * @param icon what the slot shows
 * @param action what a click does, or null for a display-only slot
 * @param hint what the button means to native clients, or null
 * @param meta native presentation; null becomes {@link MenuMeta#NONE}
 */
@ApiStatus.AvailableSince("1.0.0")
public record MenuButton(MenuIcon icon, MenuAction action, MenuHint hint, MenuMeta meta) {
  public MenuButton {
    Objects.requireNonNull(icon, "icon");
    meta = meta == null ? MenuMeta.NONE : meta;
  }

  /** A button without a hint or meta. */
  public MenuButton(MenuIcon icon, MenuAction action) {
    this(icon, action, null, MenuMeta.NONE);
  }

  /** A display-only button. */
  public static MenuButton display(MenuIcon icon) {
    return new MenuButton(icon, null, null, MenuMeta.NONE);
  }

  /** This button with another hint. */
  public MenuButton withHint(MenuHint newHint) {
    return new MenuButton(icon, action, newHint, meta);
  }

  /** This button with another meta. */
  public MenuButton withMeta(MenuMeta newMeta) {
    return new MenuButton(icon, action, hint, newMeta);
  }

  /** True when the button has an action. */
  public boolean clickable() {
    return action != null;
  }
}
