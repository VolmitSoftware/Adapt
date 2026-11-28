package art.arcane.adapt.pack.hud;

import java.util.Locale;

/** Where pack viewers see Adapt's HUD: {@link HudCorner} in the top left, {@link HudStrip} above the hotbar, or plain text. */
public enum HudPlacement {
  CORNER("corner"),
  HOTBAR("hotbar"),
  OFF("off");

  private final String key;

  HudPlacement(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  /** The placement named {@code key}, ignoring case and surrounding space, or null. */
  public static HudPlacement of(String key) {
    String wanted = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    for (HudPlacement placement : values()) {
      if (placement.key.equals(wanted)) {
        return placement;
      }
    }
    return null;
  }
}
