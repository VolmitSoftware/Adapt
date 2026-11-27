package art.arcane.adapt.pack.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy section-sign text split into runs of one colour. Colour codes and {@code §x} hex sequences set the colour,
 * {@code §r} returns to the default, and formatting codes ({@code k} to {@code o}) are dropped, since title runs carry
 * a colour only.
 */
public final class LegacyText {
  private static final char SECTION = '§';
  private static final int[] COLOURS = {
      0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
      0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
  };

  private LegacyText() {
  }

  public static List<Segment> segments(String legacy, int defaultColour) {
    List<Segment> segments = new ArrayList<>();
    if (legacy == null || legacy.isEmpty()) {
      return segments;
    }
    StringBuilder text = new StringBuilder();
    int colour = defaultColour;
    int index = 0;
    while (index < legacy.length()) {
      char c = legacy.charAt(index);
      if (c != SECTION || index + 1 >= legacy.length()) {
        text.append(c);
        index++;
        continue;
      }
      char code = Character.toLowerCase(legacy.charAt(index + 1));
      int next = colour;
      int consumed = 2;
      int digit = Character.digit(code, 16);
      if (digit >= 0) {
        next = COLOURS[digit];
      } else if (code == 'r') {
        next = defaultColour;
      } else if (code == 'x') {
        int hex = hex(legacy, index + 2);
        if (hex >= 0) {
          next = hex;
          consumed = 14;
        }
      }
      if (next != colour && !text.isEmpty()) {
        segments.add(new Segment(text.toString(), colour));
        text.setLength(0);
      }
      colour = next;
      index += consumed;
    }
    if (!text.isEmpty()) {
      segments.add(new Segment(text.toString(), colour));
    }
    return segments;
  }

  /** The text without any codes. */
  public static String plain(String legacy) {
    StringBuilder out = new StringBuilder();
    for (Segment segment : segments(legacy, 0)) {
      out.append(segment.text());
    }
    return out.toString();
  }

  private static int hex(String text, int start) {
    if (start + 12 > text.length()) {
      return -1;
    }
    int value = 0;
    for (int pair = 0; pair < 6; pair++) {
      int at = start + (pair * 2);
      int digit = Character.digit(text.charAt(at + 1), 16);
      if (text.charAt(at) != SECTION || digit < 0) {
        return -1;
      }
      value = (value << 4) | digit;
    }
    return value;
  }

  public record Segment(String text, int colour) {
  }
}
