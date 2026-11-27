package art.arcane.adapt.bukkit.feature.display;

import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.platform.entity.display.TextAlignment;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;

final class BukkitTextDisplayRef extends BukkitDisplayRef implements TextDisplayRef {
  static final int DEFAULT_BACKGROUND = 0x40000000;

  BukkitTextDisplayRef(TextDisplay display) {
    super(display);
  }

  private TextDisplay textDisplay() {
    return (TextDisplay) handle();
  }

  @Override
  public String text() {
    return BukkitText.markup(PaperApi.text(textDisplay()));
  }

  @Override
  public void setText(String markup) {
    PaperCompat.text(textDisplay(), BukkitText.component(markup));
  }

  @Override
  public int backgroundArgb() {
    Color color = textDisplay().getBackgroundColor();
    return color == null ? DEFAULT_BACKGROUND : color.asARGB();
  }

  @Override
  public void setBackgroundArgb(int argb) {
    textDisplay().setDefaultBackground(false);
    textDisplay().setBackgroundColor(Color.fromARGB(argb));
  }

  @Override
  public int lineWidth() {
    return textDisplay().getLineWidth();
  }

  @Override
  public void setLineWidth(int width) {
    textDisplay().setLineWidth(width);
  }

  @Override
  public boolean isSeeThrough() {
    return textDisplay().isSeeThrough();
  }

  @Override
  public void setSeeThrough(boolean seeThrough) {
    textDisplay().setSeeThrough(seeThrough);
  }

  @Override
  public boolean isShadowed() {
    return textDisplay().isShadowed();
  }

  @Override
  public void setShadowed(boolean shadowed) {
    textDisplay().setShadowed(shadowed);
  }

  @Override
  public TextAlignment alignment() {
    return switch (textDisplay().getAlignment()) {
      case CENTER -> TextAlignment.CENTER;
      case LEFT -> TextAlignment.LEFT;
      case RIGHT -> TextAlignment.RIGHT;
    };
  }

  @Override
  public void setAlignment(TextAlignment alignment) {
    textDisplay().setAlignment(switch (alignment) {
      case CENTER -> TextDisplay.TextAlignment.CENTER;
      case LEFT -> TextDisplay.TextAlignment.LEFT;
      case RIGHT -> TextDisplay.TextAlignment.RIGHT;
    });
  }
}
