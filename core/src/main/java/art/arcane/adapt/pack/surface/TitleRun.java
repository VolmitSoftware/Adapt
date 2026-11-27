package art.arcane.adapt.pack.surface;

import java.util.Objects;

/**
 * One run of the window title: {@code text} drawn in {@code font} (a namespaced font id) in {@code colour}
 * (0xRRGGBB). Page art and shift runs use {@link #PLAIN} so the glyph texture is not tinted.
 */
public record TitleRun(String font, String text, int colour) {
  public static final int PLAIN = 0xFFFFFF;

  public TitleRun {
    Objects.requireNonNull(font, "font");
    Objects.requireNonNull(text, "text");
    colour &= 0xFFFFFF;
  }

  public TitleRun append(String more) {
    return new TitleRun(font, text + more, colour);
  }
}
