package art.arcane.adapt.pack.text;

import java.util.Objects;

/**
 * A font text can be drawn in on the title, and the container y of its line top. {@code shifted} fonts are the vanilla
 * font re-declared lower down, which only moves bitmap scripts; the vanilla title font is not shifted.
 */
public record RowFont(String font, int y, boolean shifted) {
  public RowFont {
    Objects.requireNonNull(font, "font");
  }
}
