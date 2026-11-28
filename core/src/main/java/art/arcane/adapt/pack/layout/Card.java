package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One card seat: the slots it covers (each clicks and shows the tooltip), the slot its icon sits in, its labels and bars
 * by role ({@code name}, {@code level}, {@code xp} ...) in container pixels, and the container y of its top ({@code top}).
 */
public record Card(List<PackSlot> cells, PackSlot icon, Map<String, TextSpec> text, Map<String, BarSpec> bars, int top) {
  /** An item's height on its slot. */
  private static final int ICON_SIZE = 16;

  public Card {
    cells = List.copyOf(cells);
    Objects.requireNonNull(icon, "icon");
    text = Map.copyOf(text);
    bars = Map.copyOf(bars);
    if (!cells.contains(icon)) {
      throw new IllegalArgumentException("card icon must be one of its cells");
    }
  }

  /** The label of {@code role}, or null when the card has none. */
  public TextSpec text(String role) {
    return text.get(role);
  }

  /** The bar of {@code role}, or null when the card has none. */
  public BarSpec bar(String role) {
    return bars.get(role);
  }

  /**
   * Whether a heading may sit in the gap between {@code above} and {@code below}: the chest rows end at {@code above}'s
   * icon and the inventory rows begin at {@code below}'s.
   */
  public static boolean gap(Card above, Card below) {
    return above.icon().area() == PackSlot.Area.TOP && below.icon().area() == PackSlot.Area.LOWER;
  }

  /** A heading place in the gap between {@code above} and {@code below}: {@code below}'s heading and its rule, centred in the gap. */
  public static Card between(Card above, Card below) {
    TextSpec heading = below.text("heading");
    if (heading == null) {
      return below;
    }
    int top = ((above.top() + ICON_SIZE + below.top()) / 2) - (PackArt.LINE_HEIGHT / 2);
    int dy = top - heading.y();
    Map<String, TextSpec> text = new LinkedHashMap<>();
    text.put("heading", heading.moved(0, dy));
    Map<String, BarSpec> bars = new LinkedHashMap<>();
    BarSpec rule = below.bar("heading.rule");
    if (rule != null) {
      bars.put("heading.rule", rule.moved(0, dy));
    }
    return new Card(below.cells(), below.icon(), text, bars, below.top() + dy);
  }
}
