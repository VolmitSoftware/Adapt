package art.arcane.adapt.pack.hud;

/**
 * The codepoints, sizes and ascents of font {@code adapt:hud}, which the pack bake writes and the strip and corner
 * writers set. Bitmap glyphs advance their width plus one, as vanilla bitmap providers do; the bake pins each glyph's
 * rightmost column so the client measures the whole cell.
 */
public final class HudGlyphs {
  public static final String FONT = "adapt:hud";
  /** Space glyphs move the pen by every power of two up to this one, left from {@link #NEGATIVE_BASE}. */
  public static final int SHIFT_BITS = 18;
  public static final int NEGATIVE_BASE = 0xE000;
  public static final int POSITIVE_BASE = 0xE020;

  public static final int WELL = 0xE040;
  public static final int WELL_SIZE = 20;
  public static final int WELL_ASCENT = 17;
  public static final int WELL_ADVANCE = WELL_SIZE + 1;
  public static final int WIPE_STEPS = 8;
  /** Wipe {@code s} (1 to {@link #WIPE_STEPS}) is {@code WIPE_BASE + s}. */
  public static final int WIPE_BASE = 0xE040;
  public static final int ICON_SIZE = 16;
  public static final int ICON_ASCENT = 15;
  public static final int GLYPH_ADVANCE = ICON_SIZE + 1;
  public static final int XP_TRACK = 0xE049;
  public static final int XP_BAR_WIDTH = 60;
  public static final int XP_BAR_HEIGHT = 4;
  public static final int XP_BAR_ADVANCE = XP_BAR_WIDTH + 1;
  public static final int XP_FILL_STEPS = 30;
  public static final int XP_FILL_BASE = 0xE050;
  public static final int FALLBACK_ICON = 0xE06F;

  /** The corner's parchment slip: left edge, right edge and middles of every power-of-two width up to 2^7. */
  public static final int PLATE_LEFT = 0xE070;
  public static final int PLATE_RIGHT = 0xE071;
  public static final int PLATE_BASE = 0xE072;
  public static final int PLATE_POWERS = 8;
  public static final int PLATE_HEIGHT = 12;
  public static final int PLATE_ASCENT = 7;
  public static final int BAR_TRACK = 0xE07A;
  public static final int BAR_WIDTH = 28;
  public static final int BAR_HEIGHT = 2;
  public static final int BAR_ASCENT = 2;
  /** Bar fill {@code k} (0 to {@link #BAR_WIDTH}) covers {@code k} columns. */
  public static final int BAR_FILL_BASE = 0xE080;
  /** The skill and adaptation icons, from U+E100 in sorted sprite order. */
  public static final int FIRST_ICON = 0xE100;
  /** The same icons drawn {@link #SMALL_ICON_SIZE} tall for the corner sit this far above their full-size glyph. */
  public static final int SMALL_ICON_OFFSET = 0x0800;
  public static final int SMALL_ICON_SIZE = 10;
  public static final int SMALL_ICON_ASCENT = 7;
  public static final int SMALL_ICON_ADVANCE = SMALL_ICON_SIZE + 1;

  private HudGlyphs() {
  }

  /** Space glyphs that move the pen by {@code pixels}, left when negative; empty for 0. */
  public static String shift(int pixels) {
    if (pixels == 0) {
      return "";
    }
    int base = pixels < 0 ? NEGATIVE_BASE : POSITIVE_BASE;
    long remaining = Math.abs((long) pixels);
    StringBuilder out = new StringBuilder();
    for (int bit = SHIFT_BITS - 1; bit >= 0; bit--) {
      long step = 1L << bit;
      while (remaining >= step) {
        out.append((char) (base + bit));
        remaining -= step;
      }
    }
    return out.toString();
  }

  public static String well() {
    return glyph(WELL);
  }

  /** The shade covering {@code step} eighths of an icon, 1 to {@link #WIPE_STEPS}. */
  public static String wipe(int step) {
    if (step < 1 || step > WIPE_STEPS) {
      throw new IllegalArgumentException("wipe step out of range: " + step);
    }
    return glyph(WIPE_BASE + step);
  }

  public static String xpTrack() {
    return glyph(XP_TRACK);
  }

  /** The xp bar fill of {@code step} two-pixel columns, 0 to {@link #XP_FILL_STEPS}. */
  public static String xpFill(int step) {
    if (step < 0 || step > XP_FILL_STEPS) {
      throw new IllegalArgumentException("xp fill step out of range: " + step);
    }
    return glyph(XP_FILL_BASE + step);
  }

  public static String fallbackIcon() {
    return glyph(FALLBACK_ICON);
  }

  /** The plate middle {@code 2^power} pixels wide. */
  public static String plate(int power) {
    if (power < 0 || power >= PLATE_POWERS) {
      throw new IllegalArgumentException("plate power out of range: " + power);
    }
    return glyph(PLATE_BASE + power);
  }

  /** The corner bar fill of {@code columns}, 0 to {@link #BAR_WIDTH}. */
  public static String barFill(int columns) {
    if (columns < 0 || columns > BAR_WIDTH) {
      throw new IllegalArgumentException("bar fill out of range: " + columns);
    }
    return glyph(BAR_FILL_BASE + columns);
  }

  /** The small corner copy of the full-size icon glyph {@code icon}. */
  public static String small(String icon) {
    return glyph(icon.codePointAt(0) + SMALL_ICON_OFFSET);
  }

  public static String glyph(int codepoint) {
    return Character.toString(codepoint);
  }
}
