package art.arcane.adapt.platform.entity.display;

/** Fixed light override for a display entity, 0 to 15 each. */
public record Brightness(int block, int sky) {
  public Brightness {
    if (block < 0 || block > 15 || sky < 0 || sky > 15) {
      throw new IllegalArgumentException("Brightness out of range: " + block + "," + sky);
    }
  }
}
