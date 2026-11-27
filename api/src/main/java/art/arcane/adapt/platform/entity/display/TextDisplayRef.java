package art.arcane.adapt.platform.entity.display;

import org.jetbrains.annotations.ApiStatus;

/** Text display. Text uses Adapt markup. */
@ApiStatus.AvailableSince("1.0.0")
public interface TextDisplayRef extends DisplayRef {
  /** Text shown, as Adapt markup. */
  String text();

  /** Replaces the text. */
  void setText(String markup);

  /** ARGB background colour. */
  int backgroundArgb();

  /** Sets the ARGB background colour; alpha 0 hides the background. */
  void setBackgroundArgb(int argb);

  /** Line width in pixels before text wraps. */
  int lineWidth();

  /** Sets the line width in pixels before text wraps. */
  void setLineWidth(int width);

  /** True when the text shows through blocks. */
  boolean isSeeThrough();

  /** Shows the text through blocks or not. */
  void setSeeThrough(boolean seeThrough);

  /** True when the text draws with a drop shadow. */
  boolean isShadowed();

  /** Turns the text drop shadow on or off. */
  void setShadowed(boolean shadowed);

  /** Line alignment. */
  TextAlignment alignment();

  /** Sets the line alignment. */
  void setAlignment(TextAlignment alignment);
}
