package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grids and lists that run from the chest rows into the inventory rows keep one row pitch across the gap between the two,
 * on both tiers, and every drawn row still sits over its own slots so a click lands on the row it points at.
 */
class JoinedGridTest {
  private final PackLayout layout = PackLayoutReader.builtIn();

  @Test
  void everyColumnOfCardsKeepsOnePitchOnEveryScreenAndTier() {
    for (Map.Entry<String, ScreenLayout> entry : layout.screens().entrySet()) {
      Map<Integer, List<Integer>> columns = new TreeMap<>();
      for (Card card : entry.getValue().cards()) {
        columns.computeIfAbsent(layout.grid().x(card.icon()), (Integer x) -> new ArrayList<>()).add(card.top());
      }
      for (Map.Entry<Integer, List<Integer>> column : columns.entrySet()) {
        List<Integer> tops = column.getValue();
        for (int index = 2; index < tops.size(); index++) {
          assertThat(tops.get(index) - tops.get(index - 1)).as(entry.getKey() + " column x " + column.getKey() + " row " + index)
              .isEqualTo(tops.get(1) - tops.get(0));
        }
      }
    }
  }

  @Test
  void theGridsThatCrossTheGapAreTheSkillsCardsTheSkillPageListsAndThePickCards() {
    assertThat(crosses(layout.screen("skills", true))).isTrue();
    assertThat(crosses(layout.screen("skill", true))).isTrue();
    assertThat(crosses(layout.screen("mutate.partners", true))).isTrue();
    assertThat(layout.screen("skills", true).cards().get(9).top() - layout.screen("skills", true).cards().get(6).top()).isEqualTo(36);
    assertThat(layout.screen("skill", true).cards().get(5).top() - layout.screen("skill", true).cards().get(4).top()).isEqualTo(18);
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
  void aDrawnRowStaysOverItsOwnSlots() {
    for (Map.Entry<String, ScreenLayout> entry : layout.screens().entrySet()) {
      for (Card card : entry.getValue().cards()) {
        int middle = layout.grid().y(card.icon()) + card.shift() + 8;
        int slotTop = layout.grid().y(card.icon());
        assertThat(middle).as(entry.getKey() + " " + card.icon()).isBetween(slotTop, slotTop + 17);
      }
    }
  }

  @Test
  void settingsRowsKeepOnePitchAcrossTheGap() {
    for (boolean lower : List.of(true, false)) {
      ScreenLayout settings = layout.screen("adaptation.settings", lower);
      List<PackSlot> rows = settings.slots("pref.rows");
      for (int index = 1; index < rows.size(); index++) {
        int previous = layout.grid().y(rows.get(index - 1)) + settings.shift("pref.rows", rows.get(index - 1));
        int next = layout.grid().y(rows.get(index)) + settings.shift("pref.rows", rows.get(index));
        assertThat(next - previous).as("settings row " + index + (lower ? "" : " top rows")).isEqualTo(layout.grid().pitch());
      }
    }
  }

  @Test
  void noScreenDrawsARuleOrCaptionAlongTheGap() {
    for (Map.Entry<String, ScreenLayout> entry : layout.screens().entrySet()) {
      for (Map.Entry<String, BarSpec> bar : entry.getValue().bars().entrySet()) {
        assertThat(bar.getValue().y() < 126 || bar.getValue().y() > 138).as(entry.getKey() + " " + bar.getKey()).isTrue();
      }
      for (Map.Entry<String, TextSpec> text : entry.getValue().text().entrySet()) {
        assertThat(text.getValue().y() < 122 || text.getValue().y() > 138).as(entry.getKey() + " " + text.getKey()).isTrue();
      }
    }
  }

  private static boolean crosses(ScreenLayout screen) {
    boolean chest = false;
    boolean player = false;
    for (Card card : screen.cards()) {
      chest |= card.icon().area() == PackSlot.Area.TOP;
      player |= card.icon().area() == PackSlot.Area.LOWER;
    }
    return chest && player;
  }
}
