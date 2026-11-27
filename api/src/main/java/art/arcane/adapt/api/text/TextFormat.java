package art.arcane.adapt.api.text;

import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;

/**
 * The formatting codes of a string shown to a player. A constant's {@link #toString()} is its section-sign code, so
 * it concatenates straight into a string: {@code TextFormat.GREEN + "+ " + TextFormat.GRAY + label}.
 *
 * <p>Every string the API takes for display (stat lines, HUD text, toast text, resolved {@link Text}) uses these
 * codes. The platform turns them into components.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum TextFormat {
  BLACK('0', 0x000000),
  DARK_BLUE('1', 0x0000AA),
  DARK_GREEN('2', 0x00AA00),
  DARK_AQUA('3', 0x00AAAA),
  DARK_RED('4', 0xAA0000),
  DARK_PURPLE('5', 0xAA00AA),
  GOLD('6', 0xFFAA00),
  GRAY('7', 0xAAAAAA),
  DARK_GRAY('8', 0x555555),
  BLUE('9', 0x5555FF),
  GREEN('a', 0x55FF55),
  AQUA('b', 0x55FFFF),
  RED('c', 0xFF5555),
  LIGHT_PURPLE('d', 0xFF55FF),
  YELLOW('e', 0xFFFF55),
  WHITE('f', 0xFFFFFF),
  /** Obfuscated text that keeps changing. */
  MAGIC('k', -1),
  BOLD('l', -1),
  STRIKETHROUGH('m', -1),
  UNDERLINE('n', -1),
  ITALIC('o', -1),
  /** Clears every colour and decoration before it. */
  RESET('r', -1);

  /** The section sign that starts every formatting code. */
  public static final char CODE = '§';

  private final char character;
  private final int packedRgb;
  private final String code;

  TextFormat(char character, int packedRgb) {
    this.character = character;
    this.packedRgb = packedRgb;
    this.code = new String(new char[] {CODE, character});
  }

  /** The character after {@link #CODE}, such as {@code a} for {@link #GREEN}. */
  public char character() {
    return character;
  }

  /** Whether this is one of the sixteen colours rather than a decoration or {@link #RESET}. */
  public boolean isColor() {
    return packedRgb >= 0;
  }

  /** The colour of a colour code; throws for a decoration. */
  public Rgb rgb() {
    if (packedRgb < 0) {
      throw new IllegalStateException(name() + " is not a colour");
    }
    return Rgb.of(packedRgb);
  }

  /** The code that switches following text to an exact colour. */
  public static String of(Rgb color) {
    String hex = String.format("%06x", color.packed() & 0xFFFFFF);
    StringBuilder code = new StringBuilder(14).append(CODE).append('x');
    for (int index = 0; index < hex.length(); index++) {
      code.append(CODE).append(hex.charAt(index));
    }
    return code.toString();
  }

  /** The named colour closest to {@code color}, for surfaces that only have the sixteen. */
  public static TextFormat nearest(Rgb color) {
    TextFormat best = WHITE;
    long bestDistance = Long.MAX_VALUE;
    for (TextFormat candidate : values()) {
      if (!candidate.isColor()) {
        continue;
      }
      Rgb other = candidate.rgb();
      long red = color.red() - other.red();
      long green = color.green() - other.green();
      long blue = color.blue() - other.blue();
      long distance = red * red + green * green + blue * blue;
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return best;
  }

  /** {@code text} without any formatting codes, exact colours included. */
  public static String strip(String text) {
    if (text == null || text.indexOf(CODE) < 0) {
      return text;
    }
    StringBuilder plain = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == CODE && index + 1 < text.length()) {
        index++;
        continue;
      }
      plain.append(character);
    }
    return plain.toString();
  }

  @Override
  public String toString() {
    return code;
  }
}
