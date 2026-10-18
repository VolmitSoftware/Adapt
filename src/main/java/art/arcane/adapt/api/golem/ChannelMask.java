package art.arcane.adapt.api.golem;

import org.bukkit.DyeColor;

import java.util.ArrayList;
import java.util.List;

public final class ChannelMask {
  public static final int NONE = 0;
  public static final int ALL = 0xFFFF;

  private ChannelMask() {
  }

  public static int bit(DyeColor color) {
    return 1 << color.ordinal();
  }

  public static int of(DyeColor... colors) {
    int mask = NONE;
    for (DyeColor color : colors) {
      mask |= bit(color);
    }
    return mask;
  }

  public static int normalize(int mask) {
    return mask & ALL;
  }

  public static boolean isNone(int mask) {
    return normalize(mask) == NONE;
  }

  public static boolean contains(int mask, DyeColor color) {
    return (mask & bit(color)) != 0;
  }

  public static int with(int mask, DyeColor color) {
    return normalize(mask | bit(color));
  }

  public static int without(int mask, DyeColor color) {
    return normalize(mask & ~bit(color));
  }

  public static boolean overlaps(int first, int second) {
    return normalize(first & second) != NONE;
  }

  public static List<DyeColor> colors(int mask) {
    DyeColor[] all = DyeColor.values();
    List<DyeColor> colors = new ArrayList<>(Integer.bitCount(normalize(mask)));
    for (DyeColor color : all) {
      if (contains(mask, color)) {
        colors.add(color);
      }
    }
    return colors;
  }
}
