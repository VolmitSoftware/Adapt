package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.text.Align;

import java.util.Objects;

/**
 * Where a label goes: line top {@code y}, {@code width} pixels from {@code x}, aligned, in {@code colour}, wrapped over
 * at most {@code lines} lines {@code pitch} pixels apart (0 for the font's line height). Text needing fewer lines sits
 * centred in a label of two, unless the label is {@code top} aligned. A label that {@code breaks} splits a word too wide
 * for its line with a hyphen, as the native client does, instead of overflowing.
 */
public record TextSpec(int x, int y, int width, Align align, int colour, int lines, int pitch, boolean top, boolean breaks) {
  public TextSpec {
    Objects.requireNonNull(align, "align");
    if (width < 1 || lines < 1 || pitch < 0) {
      throw new IllegalArgumentException("text needs a width and at least one line");
    }
  }

  public TextSpec(int x, int y, int width, Align align, int colour, int lines, int pitch) {
    this(x, y, width, align, colour, lines, pitch, false, false);
  }

  /** One line at the font's line height. */
  public TextSpec(int x, int y, int width, Align align, int colour) {
    this(x, y, width, align, colour, 1, 0);
  }

  public TextSpec moved(int dx, int dy) {
    return new TextSpec(x + dx, y + dy, width, align, colour, lines, pitch, top, breaks);
  }

  public TextSpec coloured(int newColour) {
    return new TextSpec(x, y, width, align, newColour, lines, pitch, top, breaks);
  }

  /** The distance between line tops, given the font's line height. */
  public int pitch(int lineHeight) {
    return pitch > 0 ? pitch : lineHeight;
  }
}
