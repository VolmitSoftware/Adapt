package art.arcane.adapt.pack.layout;

import java.util.Map;
import java.util.Objects;

/**
 * Every screen's geometry. A screen {@code id} is laid out over the player slots; {@code id.top} is its variant for
 * windows that cannot take them.
 */
public record PackLayout(PackGrid grid, Map<String, ScreenLayout> screens) {
  public static final String TOP_ONLY = ".top";

  public PackLayout {
    Objects.requireNonNull(grid, "grid");
    screens = Map.copyOf(screens);
  }

  /** The layout of {@code id} for a window with or without the player slots. */
  public ScreenLayout screen(String id, boolean lowerSlots) {
    ScreenLayout layout = lowerSlots ? screens.get(id) : screens.get(id + TOP_ONLY);
    if (layout == null && !lowerSlots) {
      ScreenLayout full = screens.get(id);
      layout = full != null && !full.usesLower() ? full : null;
    }
    if (layout == null) {
      throw new IllegalArgumentException("no layout for screen " + id + (lowerSlots ? "" : TOP_ONLY));
    }
    return layout;
  }
}
