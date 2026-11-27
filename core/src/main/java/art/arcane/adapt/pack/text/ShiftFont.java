package art.arcane.adapt.pack.text;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The pack's space font: glyphs whose advance moves the title pen by a power of two, both ways, and nothing else.
 * {@code glyphs} maps each advance (1, 2, 4 ... and -1, -2, -4 ...) to its character.
 */
public record ShiftFont(String font, Map<Integer, String> glyphs) {
  public ShiftFont {
    Objects.requireNonNull(font, "font");
    glyphs = Map.copyOf(glyphs);
    if (!glyphs.containsKey(1) || !glyphs.containsKey(-1)) {
      throw new IllegalArgumentException("shift font needs advances 1 and -1");
    }
    for (Integer advance : glyphs.keySet()) {
      int size = Math.abs(advance);
      if (Integer.bitCount(size) != 1) {
        throw new IllegalArgumentException("shift advances must be powers of two: " + advance);
      }
    }
  }

  /** Characters whose advances add up to {@code distance}, largest first. */
  public String shift(int distance) {
    StringBuilder out = new StringBuilder();
    int sign = Integer.signum(distance);
    int left = Math.abs(distance);
    TreeMap<Integer, String> sized = new TreeMap<>();
    for (Map.Entry<Integer, String> entry : glyphs.entrySet()) {
      if (Integer.signum(entry.getKey()) == sign) {
        sized.put(Math.abs(entry.getKey()), entry.getValue());
      }
    }
    while (left > 0) {
      Map.Entry<Integer, String> step = sized.floorEntry(left);
      out.append(step.getValue());
      left -= step.getKey();
    }
    return out.toString();
  }
}
