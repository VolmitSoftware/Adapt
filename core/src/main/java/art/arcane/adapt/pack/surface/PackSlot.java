package art.arcane.adapt.pack.surface;

import java.util.Locale;
import java.util.Objects;

/**
 * A slot of a pack window: one of the chest's own slots ({@link Area#TOP}, 0 to 53) or one of the 36 player slots
 * below it ({@link Area#LOWER}: three inventory rows, then the hotbar row, 0 to 35). Written {@code top:4} or
 * {@code lower:27} in manifests and layouts.
 */
public record PackSlot(Area area, int index) implements Comparable<PackSlot> {
  public static final int LOWER_SLOTS = 36;

  public PackSlot {
    Objects.requireNonNull(area, "area");
    int limit = area == Area.TOP ? 54 : LOWER_SLOTS;
    if (index < 0 || index >= limit) {
      throw new IllegalArgumentException(area + " slot out of range: " + index);
    }
  }

  public static PackSlot top(int index) {
    return new PackSlot(Area.TOP, index);
  }

  public static PackSlot lower(int index) {
    return new PackSlot(Area.LOWER, index);
  }

  public static PackSlot parse(String text) {
    int colon = text == null ? -1 : text.indexOf(':');
    if (colon < 0) {
      throw new IllegalArgumentException("slot must be top:<n> or lower:<n>: " + text);
    }
    Area area = Area.valueOf(text.substring(0, colon).trim().toUpperCase(Locale.ROOT));
    return new PackSlot(area, Integer.parseInt(text.substring(colon + 1).trim()));
  }

  /** The slot {@code rows} and {@code columns} away in the same area. */
  public PackSlot offset(int rows, int columns) {
    return new PackSlot(area, index + (rows * 9) + columns);
  }

  public int row() {
    return index / 9;
  }

  public int column() {
    return index % 9;
  }

  /** The slot number a click on this slot carries in a menu of {@code menuSize} chest slots. */
  public int clickSlot(int menuSize) {
    return area == Area.TOP ? index : menuSize + index;
  }

  @Override
  public int compareTo(PackSlot other) {
    int byArea = area.compareTo(other.area);
    return byArea != 0 ? byArea : Integer.compare(index, other.index);
  }

  @Override
  public String toString() {
    return area.name().toLowerCase(Locale.ROOT) + ":" + index;
  }

  public enum Area {
    TOP,
    LOWER
  }
}
