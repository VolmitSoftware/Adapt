package art.arcane.adapt.platform.menu;

import java.util.Objects;

/** Slot content. A null action makes the slot display-only. Clicks never move the icon item. */
public record MenuButton(MenuIcon icon, MenuAction action) {
  public MenuButton {
    Objects.requireNonNull(icon, "icon");
  }

  public static MenuButton display(MenuIcon icon) {
    return new MenuButton(icon, null);
  }

  public boolean clickable() {
    return action != null;
  }
}
