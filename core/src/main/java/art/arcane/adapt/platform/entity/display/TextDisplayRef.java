package art.arcane.adapt.platform.entity.display;

/** Text display. Text uses Adapt markup. */
public interface TextDisplayRef extends DisplayRef {
  String text();

  void setText(String markup);

  /** ARGB background colour. */
  int backgroundArgb();

  void setBackgroundArgb(int argb);

  int lineWidth();

  void setLineWidth(int width);

  boolean isSeeThrough();

  void setSeeThrough(boolean seeThrough);

  boolean isShadowed();

  void setShadowed(boolean shadowed);

  TextAlignment alignment();

  void setAlignment(TextAlignment alignment);
}
