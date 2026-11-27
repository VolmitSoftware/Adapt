package art.arcane.adapt.pack.screen;

import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.platform.menu.ChestMenu;

import java.util.Objects;

/** One rendered pack page: the chest menu with its click handlers and what the pack window adds to it. */
public record PackPage(ChestMenu menu, PackSurface surface) {
  public PackPage {
    Objects.requireNonNull(menu, "menu");
    Objects.requireNonNull(surface, "surface");
  }
}
