package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

/** 24-bit colour. Each channel is 0 to 255; the constructor throws {@link IllegalArgumentException} otherwise. */
@ApiStatus.AvailableSince("1.0.0")
public record Rgb(int red, int green, int blue) {
  public Rgb {
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
      throw new IllegalArgumentException("Colour channel out of range: " + red + "," + green + "," + blue);
    }
  }

  /** Reads a {@code 0xRRGGBB} value; bits above the low 24 are ignored. */
  public static Rgb of(int packedRgb) {
    return new Rgb((packedRgb >> 16) & 0xFF, (packedRgb >> 8) & 0xFF, packedRgb & 0xFF);
  }

  /** The colour as {@code 0xRRGGBB}. */
  public int packed() {
    return (red << 16) | (green << 8) | blue;
  }
}
