package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every row a player can hover sits on its own slots, no card spans the gap between the chest rows and the inventory
 * rows, and no page text or rule lies on the row just below a slot, where the hover highlight paints paper.
 */
class SlotRowsTest {
  private static final int GLYPH_ROWS = 8;
  private final PackLayout layout = PackLayoutReader.builtIn();

  @Test
  void everyCardIsDrawnOnItsOwnSlots() {
    for (Map.Entry<String, ScreenLayout> entry : layout.screens().entrySet()) {
      for (Card card : entry.getValue().cards()) {
        assertThat(card.top()).as(entry.getKey() + " " + card.icon()).isEqualTo(layout.grid().y(card.cells().get(0)));
      }
    }
  }

  @Test
  void noCardSpansTheGap() {
    for (Map.Entry<String, ScreenLayout> entry : layout.screens().entrySet()) {
      for (Card card : entry.getValue().cards()) {
        Set<PackSlot.Area> areas = new TreeSet<>();
        card.cells().forEach((PackSlot cell) -> areas.add(cell.area()));
        assertThat(areas).as(entry.getKey() + " " + card.icon()).hasSize(1);
      }
    }
  }

  @Test
  void theShelfLeafRunsItsKeptAndToolRowsThroughTheMiddleColumnOnItsStillPage() {
    for (boolean lower : List.of(true, false)) {
      ScreenLayout shelf = layout.screen("skill.shelf", lower);
      assertThat(shelf.slots(PackArt.ART)).isEmpty();
      for (String role : List.of("kept", "tools")) {
        List<PackSlot> slots = shelf.slots(role);
        assertThat(slots.size() % 9).as(role).isZero();
        for (int index = 1; index < slots.size(); index++) {
          assertThat(slots.get(index).index() - slots.get(index - 1).index()).as(role + " " + index).isEqualTo(1);
        }
      }
    }
  }

  @Test
  void noTextOrRuleLiesOnAHoverRow() {
    Set<Integer> hover = new TreeSet<>();
    for (int rowY : PackArt.ROWS) {
      hover.add(rowY + 16);
    }
    for (Map.Entry<String, ScreenLayout> entry : layout.screens().entrySet()) {
      ScreenLayout screen = entry.getValue();
      for (Map.Entry<String, TextSpec> text : screen.text().entrySet()) {
        for (TextSpec placed : placed(screen, text.getKey(), text.getValue(), PackArt.ROW_TEXT)) {
          assertThat(rows(placed)).as(entry.getKey() + " " + text.getKey()).doesNotContainAnyElementsOf(hover);
        }
      }
      for (Card card : screen.cards()) {
        card.text().forEach((String role, TextSpec text) ->
            assertThat(rows(text)).as(entry.getKey() + " card " + card.icon() + " " + role).doesNotContainAnyElementsOf(hover));
        card.bars().forEach((String role, BarSpec bar) ->
            assertThat(hover).as(entry.getKey() + " card " + card.icon() + " " + role).doesNotContain(bar.y()));
      }
      for (Map.Entry<String, BarSpec> bar : screen.bars().entrySet()) {
        String rows = PackArt.ROW_RULES.get(bar.getKey());
        List<Integer> ys = new ArrayList<>();
        if (rows == null) {
          ys.add(bar.getValue().y());
        } else {
          screen.slots(rows).forEach((PackSlot slot) -> ys.add(layout.grid().y(slot) + bar.getValue().y()));
        }
        assertThat(ys).as(entry.getKey() + " " + bar.getKey()).doesNotContainAnyElementsOf(hover);
      }
    }
  }

  private List<TextSpec> placed(ScreenLayout screen, String role, TextSpec text, Map<String, String> repeated) {
    String rows = repeated.get(role);
    if (rows == null) {
      return List.of(text);
    }
    List<TextSpec> placed = new ArrayList<>();
    for (PackSlot slot : screen.slots(rows)) {
      placed.add(text.moved(0, layout.grid().y(slot)));
    }
    return placed;
  }

  private static List<Integer> rows(TextSpec text) {
    List<Integer> rows = new ArrayList<>();
    int pitch = text.pitch(PackArt.LINE_HEIGHT);
    for (int line = 0; line < text.lines(); line++) {
      for (int row = 0; row < GLYPH_ROWS; row++) {
        rows.add(text.y() + (line * pitch) + row);
      }
    }
    return rows;
  }
}
