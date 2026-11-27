package art.arcane.adapt.pack.layout;

/**
 * A bar or rule drawn from title glyphs: its box in container pixels, and the colour of a rule (a bar takes the colour
 * its screen gives it).
 */
public record BarSpec(int x, int y, int width, int height, int colour) {
  public BarSpec {
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("a bar needs a width and a height");
    }
  }

  public BarSpec moved(int dx, int dy) {
    return new BarSpec(x + dx, y + dy, width, height, colour);
  }

  /** The same bar from {@code from} to its right end; null when nothing is left. */
  public BarSpec from(int from) {
    int start = Math.max(x, from);
    int right = x + width;
    return start >= right ? null : new BarSpec(start, y, right - start, height, colour);
  }
}
