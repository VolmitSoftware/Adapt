package art.arcane.adapt.pack.screen;

import art.arcane.adapt.pack.layout.BarSpec;
import art.arcane.adapt.pack.layout.Card;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.PackLayout;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.text.Align;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PackPipsTest {
  private static final BarSpec MARGIN = new BarSpec(-26, 1, 22, 2, 0xC4B08A);

  @Test
  void pipsFitOneRowWhenTheCountIsWideEnough() {
    List<BarSpec> boxes = PackSkillScreen.pipBoxes(MARGIN, Align.RIGHT, 17, 5);
    assertThat(boxes).hasSize(5);
    assertThat(boxes).allMatch((BarSpec box) -> box.y() == 1);
    assertThat(boxes.get(0).x()).isEqualTo(-4 - 14);
    assertThat(boxes.get(4).x() + boxes.get(4).width()).isEqualTo(-4);
  }

  @Test
  void pipsWiderThanTheCountTakeTwoRowsWithinIt() {
    List<BarSpec> boxes = PackSkillScreen.pipBoxes(MARGIN, Align.RIGHT, 17, 7);
    assertThat(boxes).hasSize(7);
    assertThat(boxes.subList(0, 4)).allMatch((BarSpec box) -> box.y() == 1);
    assertThat(boxes.subList(4, 7)).allMatch((BarSpec box) -> box.y() == 4);
    assertThat(boxes).allMatch((BarSpec box) -> box.x() >= -4 - 17 && box.x() + box.width() <= -4);
  }

  @Test
  void leftAlignedPipsStartAtTheCount() {
    BarSpec right = new BarSpec(74, 1, 22, 2, 0xC4B08A);
    List<BarSpec> boxes = PackSkillScreen.pipBoxes(right, Align.LEFT, 17, 7);
    assertThat(boxes.get(0).x()).isEqualTo(74);
    assertThat(boxes.get(4).x()).isEqualTo(74);
    assertThat(boxes).allMatch((BarSpec box) -> box.x() + box.width() <= 74 + 17);
  }

  @Test
  void pipsThatFitNoTwoRowsAreLeftOut() {
    assertThat(PackSkillScreen.pipBoxes(MARGIN, Align.RIGHT, 5, 9)).isEmpty();
    assertThat(PackSkillScreen.pipBoxes(MARGIN, Align.RIGHT, 17, 0)).isEmpty();
  }

  @Test
  void theBakeHasPaintForBothPipRowsOfEveryRow() {
    PackLayout layout = PackLayoutReader.builtIn();
    Set<String> paint = new HashSet<>();
    PackArt.paint(layout).forEach((PackArt.Paint piece) -> paint.add(piece.key()));
    int checked = 0;
    for (boolean lower : List.of(true, false)) {
      for (Card card : layout.screen("skill", lower).cards()) {
        BarSpec pips = card.bar(PackArt.PIPS);
        if (pips == null) {
          continue;
        }
        BarSpec second = pips.moved(0, PackArt.pipRowY(pips, 1) - pips.y());
        assertThat(paint).contains(PackArt.fill(1, pips), PackArt.fill(1, second));
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(0);
  }
}
