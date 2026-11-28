package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One card seat: the slots it covers (each clicks and shows the tooltip), the slot its icon sits in, its labels and bars
 * by role ({@code name}, {@code level}, {@code xp} ...) in container pixels, and the container y of its top ({@code top}).
 */
public record Card(List<PackSlot> cells, PackSlot icon, Map<String, TextSpec> text, Map<String, BarSpec> bars, int top) {
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
}
