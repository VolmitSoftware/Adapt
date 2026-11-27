package art.arcane.adapt.pack.hud;

/**
 * The codepoints of font {@code adapt:hud} the baked pack provides for the action bar strip. Bitmap glyphs advance their
 * width plus one, as vanilla bitmap providers do.
 */
public final class HudStripFont {
  public static final String FONT = "adapt:hud";
  public static final int WIPE_STEPS = 8;
  public static final int XP_FILL_STEPS = 30;
  public static final int WELL_ADVANCE = 21;
  public static final int GLYPH_ADVANCE = 17;
  public static final int XP_BAR_ADVANCE = 61;

  private static final int NEGATIVE_BASE = 0xE000;
  private static final int POSITIVE_BASE = 0xE008;
  private static final int SHIFT_BITS = 8;
  private static final int WELL = 0xE010;
  private static final int WIPE_BASE = 0xE010;
  private static final int XP_TRACK = 0xE019;
  private static final int XP_FILL_BASE = 0xE020;
  private static final int FALLBACK_ICON = 0xE03F;

  private HudStripFont() {
  }

  /** Space glyphs that move the pen by {@code pixels}, left when negative; empty for 0. */
  public static String shift(int pixels) {
    if (pixels == 0) {
      return "";
    }
    int base = pixels < 0 ? NEGATIVE_BASE : POSITIVE_BASE;
    int remaining = Math.abs(pixels);
    StringBuilder out = new StringBuilder();
    for (int bit = SHIFT_BITS - 1; bit >= 0; bit--) {
      int step = 1 << bit;
      while (remaining >= step) {
        out.append((char) (base + bit));
        remaining -= step;
      }
    }
    return out.toString();
  }

  public static String well() {
    return String.valueOf((char) WELL);
  }

  /** The shade covering {@code step} eighths of an icon, 1 to {@link #WIPE_STEPS}. */
  public static String wipe(int step) {
    if (step < 1 || step > WIPE_STEPS) {
      throw new IllegalArgumentException("wipe step out of range: " + step);
    }
    return String.valueOf((char) (WIPE_BASE + step));
  }

  public static String xpTrack() {
    return String.valueOf((char) XP_TRACK);
  }

  /** The xp bar fill of {@code step} two-pixel columns, 0 to {@link #XP_FILL_STEPS}. */
  public static String xpFill(int step) {
    if (step < 0 || step > XP_FILL_STEPS) {
      throw new IllegalArgumentException("xp fill step out of range: " + step);
    }
    return String.valueOf((char) (XP_FILL_BASE + step));
  }

  public static String fallbackIcon() {
    return String.valueOf((char) FALLBACK_ICON);
  }
}
