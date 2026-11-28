package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The geometry of one screen: the page art kind and rectangle, where its crumbs show on the ribbon, card seats in order,
 * slots by role, labels by role, and bars and rules by role.
 * Roles a screen uses but a layout leaves out are simply not drawn.
 */
public record ScreenLayout(String id, String pageKind, Rect page, CrumbRibbon ribbon, List<Card> cards, Map<String, List<PackSlot>> slots,
                           Map<String, TextSpec> text, Map<String, BarSpec> bars) {
  public ScreenLayout {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(pageKind, "pageKind");
    Objects.requireNonNull(page, "page");
    Objects.requireNonNull(ribbon, "ribbon");
    cards = List.copyOf(cards);
    slots = Map.copyOf(slots);
    text = Map.copyOf(text);
    bars = Map.copyOf(bars);
  }

  /** The first slot of {@code role}, or null. */
  public PackSlot slot(String role) {
    List<PackSlot> listed = slots.get(role);
    return listed == null || listed.isEmpty() ? null : listed.get(0);
  }

  public List<PackSlot> slots(String role) {
    List<PackSlot> listed = slots.get(role);
    return listed == null ? List.of() : listed;
  }

  /** The label spec of {@code role}, or null. */
  public TextSpec text(String role) {
    return text.get(role);
  }

  /** The bar or rule of {@code role}, or null. */
  public BarSpec bar(String role) {
    return bars.get(role);
  }

  /** True when the layout uses the player slots. */
  public boolean usesLower() {
    for (Card card : cards) {
      for (PackSlot cell : card.cells()) {
        if (cell.area() == PackSlot.Area.LOWER) {
          return true;
        }
      }
    }
    for (List<PackSlot> listed : slots.values()) {
      for (PackSlot slot : listed) {
        if (slot.area() == PackSlot.Area.LOWER) {
          return true;
        }
      }
    }
    return false;
  }

  static List<Card> expand(List<CardGrid> grids, PackGrid grid) {
    List<Card> cards = new ArrayList<>();
    for (CardGrid cardGrid : grids) {
      cards.addAll(cardGrid.cards(grid));
    }
    return cards;
  }
}
