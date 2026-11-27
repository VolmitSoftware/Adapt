package art.arcane.adapt.pack.text;

/**
 * The ink a skill's colour is written in on pack pages, derived the way the native screens derive theirs (client
 * {@code Palette.accent}): the colour's hue at an ink's muted saturation, darkened until it reads on the darkest paper.
 * A light colour becomes a lighter, warmer ink than the dark colour of the same hue, golds turn to rust so they stay
 * apart from yellows, and greys become taupe, slate or umber by how light they were.
 */
public final class PackInk {
  /** The darkest paper a skill's colour is written on. */
  public static final int PAPER = 0xDCC9A0;
  /** Contrast on {@link #PAPER} of the ink for a mid-light source colour; light sources get less, dark ones more. */
  private static final double MID_CONTRAST = 6.1;
  private static final double CONTRAST_SPREAD = 1.4;
  /** Degrees a light colour's hue leans toward yellow and a dark one's toward blue, so a pair of one hue stays apart. */
  private static final float HUE_LEAN = 14.0F;
  private static final float GREY = 0.15F;
  private static final float MIN_SATURATION = 0.45F;
  private static final float MAX_SATURATION = 0.62F;

  private PackInk() {
  }

  /** The 0xRRGGBB ink for the skill colour {@code rgb}. */
  public static int of(int rgb) {
    float red = (rgb >> 16 & 0xFF) / 255.0F;
    float green = (rgb >> 8 & 0xFF) / 255.0F;
    float blue = (rgb & 0xFF) / 255.0F;
    float max = Math.max(red, Math.max(green, blue));
    float min = Math.min(red, Math.min(green, blue));
    float lightness = (max + min) / 2.0F;
    float spread = max - min;
    float saturation = spread == 0.0F ? 0.0F : spread / (1.0F - Math.abs(2.0F * lightness - 1.0F));
    float bias = Math.max(-1.0F, Math.min(1.0F, (lightness - 0.5F) * 6.0F));
    double target = MID_CONTRAST - CONTRAST_SPREAD * bias;
    float hue;
    if (saturation < GREY) {
      hue = lightness > 0.9F ? 34.0F : lightness > 0.5F ? 210.0F : 28.0F;
      saturation = lightness > 0.9F ? 0.30F : lightness > 0.5F ? 0.22F : 0.18F;
      target = lightness > 0.9F ? 4.8 : lightness > 0.5F ? 5.6 : lightness > 0.1F ? 7.0 : 10.0;
    } else {
      hue = hue(red, green, blue, max, spread);
      float goal = bias > 0.0F ? 60.0F : 240.0F;
      float turn = (goal - hue + 540.0F) % 360.0F - 180.0F;
      hue = (hue + Math.max(-HUE_LEAN, Math.min(HUE_LEAN, turn)) * Math.abs(bias) + 360.0F) % 360.0F;
      if (hue >= 30.0F && hue <= 48.0F) {
        hue -= 18.0F;
      }
      saturation = Math.max(MIN_SATURATION, Math.min(MAX_SATURATION, saturation));
    }
    float low = 0.0F;
    float high = 0.6F;
    for (int step = 0; step < 24; step++) {
      float middle = (low + high) / 2.0F;
      if (contrast(hsl(hue, saturation, middle), PAPER) >= target) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return hsl(hue, saturation, low);
  }

  /** The sixteen chat colours, by their legacy code {@code 0} to {@code f}. */
  public static final int[] CHAT = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555,
      0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
  private static final String DIGITS = "0123456789abcdef";
  private static final char SECTION = '§';
  /** A hex colour code: the x and six digit pairs after its section sign. */
  private static final int HEX_CODE = 14;

  /**
   * {@code legacy} text with every chat colour code (the colours server text and markup carry, {@code §a}, {@code §7}
   * ...) written as the hex code of its ink, so it reads on parchment; hex colours, which only the pack's own design
   * palette writes, and format codes stay as they are.
   */
  public static String inked(String legacy) {
    if (legacy == null || legacy.indexOf(SECTION) < 0) {
      return legacy;
    }
    StringBuilder out = new StringBuilder(legacy.length() + 16);
    int index = 0;
    while (index < legacy.length()) {
      char c = legacy.charAt(index);
      if (c != SECTION || index + 1 >= legacy.length()) {
        out.append(c);
        index++;
        continue;
      }
      char code = Character.toLowerCase(legacy.charAt(index + 1));
      if (code == 'x') {
        int end = Math.min(legacy.length(), index + HEX_CODE);
        out.append(legacy, index, end);
        index = end;
        continue;
      }
      int chat = DIGITS.indexOf(code);
      if (chat < 0) {
        out.append(c).append(legacy.charAt(index + 1));
      } else {
        out.append(SECTION).append('x');
        for (char digit : String.format("%06x", of(CHAT[chat])).toCharArray()) {
          out.append(SECTION).append(digit);
        }
      }
      index += 2;
    }
    return out.toString();
  }

  /** The WCAG contrast ratio of two 0xRRGGBB colours. */
  public static double contrast(int first, int second) {
    double a = luminance(first);
    double b = luminance(second);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }

  private static double luminance(int rgb) {
    return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
  }

  private static double linear(int channel) {
    double value = channel / 255.0;
    return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }

  private static float hue(float red, float green, float blue, float max, float spread) {
    float sector = max == red ? (green - blue) / spread % 6.0F : max == green ? (blue - red) / spread + 2.0F : (red - green) / spread + 4.0F;
    return (sector * 60.0F + 360.0F) % 360.0F;
  }

  private static int hsl(float hue, float saturation, float lightness) {
    float chroma = (1.0F - Math.abs(2.0F * lightness - 1.0F)) * saturation;
    float sector = hue / 60.0F;
    float second = chroma * (1.0F - Math.abs(sector % 2.0F - 1.0F));
    float red = sector < 1.0F || sector >= 5.0F ? chroma : sector < 2.0F || sector >= 4.0F ? second : 0.0F;
    float green = sector < 1.0F ? second : sector < 3.0F ? chroma : sector < 4.0F ? second : 0.0F;
    float blue = sector < 2.0F ? 0.0F : sector < 3.0F ? second : sector < 5.0F ? chroma : second;
    float lift = lightness - chroma / 2.0F;
    return channel(red + lift) << 16 | channel(green + lift) << 8 | channel(blue + lift);
  }

  private static int channel(float value) {
    return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
  }
}
