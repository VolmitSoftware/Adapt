package art.arcane.adapt.platform.menu;

import java.util.Objects;

/**
 * Slot content. A null action makes the slot display-only. Clicks never move the icon item. {@code hint} may be
 * null; see {@link MenuHint}. {@code meta} says how native clients present the button; see {@link MenuMeta}.
 */
public record MenuButton(MenuIcon icon, MenuAction action, MenuHint hint, MenuMeta meta) {
  public MenuButton {
    Objects.requireNonNull(icon, "icon");
    meta = meta == null ? MenuMeta.NONE : meta;
  }

  public MenuButton(MenuIcon icon, MenuAction action) {
    this(icon, action, null, MenuMeta.NONE);
  }

  public static MenuButton display(MenuIcon icon) {
    return new MenuButton(icon, null, null, MenuMeta.NONE);
  }

  public MenuButton withHint(MenuHint newHint) {
    return new MenuButton(icon, action, newHint, meta);
  }

  public MenuButton withMeta(MenuMeta newMeta) {
    return new MenuButton(icon, action, hint, newMeta);
  }

  public boolean clickable() {
    return action != null;
  }
}
