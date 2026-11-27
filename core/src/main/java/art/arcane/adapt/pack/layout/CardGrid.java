package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A block of card seats: {@code columns} by {@code rows} cards from {@code origin}, each {@code width} by
 * {@code height} slots, its icon at {@code iconRow}, {@code iconColumn} inside the card, its labels placed relative to
 * the card's top-left slot, its bars likewise. Seats run along each row of cards, then down. A grid from a chest slot
 * may run on into the three inventory rows; such a grid is drawn at one row pitch throughout, its chest rows
 * {@link PackArt#CHEST_SHIFT} and its inventory rows {@link PackArt#LOWER_SHIFT} pixels off their slots.
 */
public record CardGrid(PackSlot origin, int columns, int rows, int width, int height, int iconRow, int iconColumn,
                       Map<String, TextSpec> text, Map<String, BarSpec> bars) {
  /** The chest rows, then the inventory rows a grid from a chest slot may run into. */
  static final int CHEST_ROWS = 6;
  static final int JOINED_ROWS = 9;
  private static final int LOWER_ROWS = 4;

  public CardGrid {
    Objects.requireNonNull(origin, "origin");
    text = Map.copyOf(text);
    bars = Map.copyOf(bars);
    int areaRows = origin.area() == PackSlot.Area.TOP ? JOINED_ROWS : LOWER_ROWS;
    if (columns < 1 || rows < 1 || width < 1 || height < 1 || iconRow >= height || iconColumn >= width
        || origin.column() + (columns * width) > 9 || origin.row() + (rows * height) > areaRows) {
      throw new IllegalArgumentException("bad card grid from " + origin);
    }
  }

  /** True when the grid runs from the chest rows into the inventory rows. */
  public boolean joined() {
    return origin.area() == PackSlot.Area.TOP && origin.row() + (rows * height) > CHEST_ROWS;
  }

  public List<Card> cards(PackGrid grid) {
    List<Card> cards = new ArrayList<>();
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        int top = origin.row() + (row * height);
        int left = origin.column() + (column * width);
        List<PackSlot> cells = new ArrayList<>();
        for (int dy = 0; dy < height; dy++) {
          for (int dx = 0; dx < width; dx++) {
            cells.add(slot(top + dy, left + dx));
          }
        }
        PackSlot corner = slot(top, left);
        int x = grid.x(corner);
        int y = grid.y(corner) + shift(corner);
        Map<String, TextSpec> placed = new LinkedHashMap<>();
        for (Map.Entry<String, TextSpec> entry : text.entrySet()) {
          placed.put(entry.getKey(), entry.getValue().moved(x, y));
        }
        Map<String, BarSpec> barsPlaced = new LinkedHashMap<>();
        for (Map.Entry<String, BarSpec> entry : bars.entrySet()) {
          barsPlaced.put(entry.getKey(), entry.getValue().moved(x, y));
        }
        PackSlot icon = slot(top + iconRow, left + iconColumn);
        cards.add(new Card(cells, icon, placed, barsPlaced, y, shift(icon)));
      }
    }
    return cards;
  }

  /** The slot of grid row {@code row} (counted from the origin's area, chest rows first) and {@code column}. */
  private PackSlot slot(int row, int column) {
    if (origin.area() == PackSlot.Area.TOP && row >= CHEST_ROWS) {
      return PackSlot.lower(((row - CHEST_ROWS) * 9) + column);
    }
    return new PackSlot(origin.area(), (row * 9) + column);
  }

  /** How far the grid draws {@code slot}'s row below the slot. */
  private int shift(PackSlot slot) {
    if (!joined()) {
      return 0;
    }
    return slot.area() == PackSlot.Area.TOP ? PackArt.CHEST_SHIFT : PackArt.LOWER_SHIFT;
  }
}
