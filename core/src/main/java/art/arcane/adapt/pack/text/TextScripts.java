package art.arcane.adapt.pack.text;

/**
 * Which text the pack can move off the title line. The client draws Latin, Greek and Cyrillic from bitmap fonts, which
 * the pack re-declares at other heights; everything else comes from unifont, which has no height to move, so such a
 * label is drawn on the title line or shown in the tooltip only. Decided per string.
 */
public final class TextScripts {
  private static final int LAST_BITMAP_COMMON = 0x2FFF;

  private TextScripts() {
  }

  public static boolean movable(String text) {
    for (int index = 0; index < text.length(); ) {
      int codePoint = text.codePointAt(index);
      if (!movable(codePoint)) {
        return false;
      }
      index += Character.charCount(codePoint);
    }
    return true;
  }

  static boolean movable(int codePoint) {
    return switch (Character.UnicodeScript.of(codePoint)) {
      case LATIN, GREEK, CYRILLIC, INHERITED -> true;
      case COMMON -> codePoint <= LAST_BITMAP_COMMON;
      default -> false;
    };
  }
}
