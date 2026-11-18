package art.arcane.adapt.platform.value;

/** 24-bit colour. */
public record Rgb(int red, int green, int blue) {
  public Rgb {
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
      throw new IllegalArgumentException("Colour channel out of range: " + red + "," + green + "," + blue);
    }
  }

  public static Rgb of(int packedRgb) {
    return new Rgb((packedRgb >> 16) & 0xFF, (packedRgb >> 8) & 0xFF, packedRgb & 0xFF);
  }

  public int packed() {
    return (red << 16) | (green << 8) | blue;
  }
}
