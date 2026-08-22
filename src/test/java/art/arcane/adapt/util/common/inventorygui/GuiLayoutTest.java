package art.arcane.adapt.util.common.inventorygui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GuiLayoutTest {
  @Test
  void everySupportedRowIsCenteredAndMirrored() {
    for (int count = 1; count <= GuiLayout.WIDTH; count++) {
      Set<Integer> positions = new HashSet<>();
      for (int index = 0; index < count; index++) {
        int position = GuiLayout.centeredPosition(index, count);
        int mirrored = GuiLayout.centeredPosition(count - index - 1, count);
        positions.add(position);
        assertThat(position).isBetween(-4, 4);
        assertThat(position).isEqualTo(-mirrored);
      }
      assertThat(positions).hasSize(count);
    }
  }

  @Test
  void fiveColumnCardsUseEvenSpacingAndCenteredRows() {
    assertThat(positions(5)).containsExactly(-4, -2, 0, 2, 4);
    assertThat(positions(4)).containsExactly(-3, -1, 1, 3);
    assertThat(positions(2)).containsExactly(-1, 1);
    assertThat(GuiLayout.centeredFiveRow(0, 1, 5)).isEqualTo(2);
    assertThat(GuiLayout.centeredFiveRow(0, 2, 5)).isEqualTo(1);
    assertThat(GuiLayout.centeredFiveRow(1, 2, 5)).isEqualTo(3);
    assertThat(GuiLayout.centeredFiveRow(0, 4, 5)).isEqualTo(0);
    assertThat(GuiLayout.centeredFiveRow(2, 4, 5)).isEqualTo(3);
  }

  @Test
  void reducedContentSpansStackRowsFromTheTopAndStayCentered() {
    assertThat(GuiLayout.centeredFiveRow(0, 1, 1)).isZero();
    assertThat(GuiLayout.centeredFiveRow(0, 3, 3)).isZero();
    assertThat(GuiLayout.centeredFiveRow(2, 3, 3)).isEqualTo(2);
    assertThat(GuiLayout.centeredFiveRow(0, 1, 3)).isEqualTo(1);
    assertThat(GuiLayout.centeredFiveRow(0, 2, 4)).isEqualTo(1);
    assertThat(GuiLayout.centeredFiveRow(1, 2, 4)).isEqualTo(2);
    assertThat(GuiLayout.centeredFiveRow(3, 9, 3)).isEqualTo(2);
  }

  @Test
  void autoSizedSkillPagesGrowOneContentRowPerFiveCards() {
    assertThat(GuiLayout.planFiveWide(0, 0).rows()).isEqualTo(2);
    assertThat(GuiLayout.planFiveWide(1, 0).rows()).isEqualTo(2);
    assertThat(GuiLayout.planFiveWide(5, 0).rows()).isEqualTo(2);
    assertThat(GuiLayout.planFiveWide(6, 0).rows()).isEqualTo(3);
    assertThat(GuiLayout.planFiveWide(11, 0).rows()).isEqualTo(4);
    assertThat(GuiLayout.planFiveWide(20, 0).rows()).isEqualTo(5);
    assertThat(GuiLayout.planFiveWide(25, 0).rows()).isEqualTo(6);
    assertThat(GuiLayout.planFiveWide(26, 0).rows()).isEqualTo(6);
    assertThat(GuiLayout.planFiveWide(500, 0).rows()).isEqualTo(6);
  }

  @Test
  void autoSizedPagingMatchesTheFiveByFiveGrid() {
    GuiLayout.PagePlan small = GuiLayout.planFiveWide(12, 0);
    assertThat(small.contentRows()).isEqualTo(3);
    assertThat(small.itemsPerPage()).isEqualTo(15);
    assertThat(small.pageCount()).isEqualTo(1);
    assertThat(small.hasNavigationRow()).isTrue();

    GuiLayout.PagePlan overflowing = GuiLayout.planFiveWide(26, 0);
    assertThat(overflowing.contentRows()).isEqualTo(5);
    assertThat(overflowing.itemsPerPage()).isEqualTo(25);
    assertThat(overflowing.pageCount()).isEqualTo(2);
  }

  @Test
  void fixedRowCountsKeepTheirHeightAndPageTheRemainder() {
    GuiLayout.PagePlan three = GuiLayout.planFiveWide(40, 3);
    assertThat(three.rows()).isEqualTo(3);
    assertThat(three.contentRows()).isEqualTo(2);
    assertThat(three.itemsPerPage()).isEqualTo(10);
    assertThat(three.pageCount()).isEqualTo(4);

    GuiLayout.PagePlan two = GuiLayout.planFiveWide(3, 2);
    assertThat(two.rows()).isEqualTo(2);
    assertThat(two.itemsPerPage()).isEqualTo(5);
    assertThat(two.pageCount()).isEqualTo(1);
  }

  @Test
  void planClampsOutOfRangeRowCountsAndNegativeItemCounts() {
    assertThat(GuiLayout.planFiveWide(30, 9).rows()).isEqualTo(6);
    assertThat(GuiLayout.planFiveWide(30, 1).rows()).isEqualTo(2);
    assertThat(GuiLayout.planFiveWide(30, -4).rows()).isEqualTo(6);
    assertThat(GuiLayout.planFiveWide(-10, 0).rows()).isEqualTo(2);
    assertThat(GuiLayout.planFiveWide(-10, 0).pageCount()).isEqualTo(1);
  }

  @Test
  void autoSizedViewportFollowsThePageWhileFixedViewportsDoNot() {
    assertThat(GuiLayout.fiveWideRowsForPage(0, 0)).isEqualTo(2);
    assertThat(GuiLayout.fiveWideRowsForPage(1, 0)).isEqualTo(2);
    assertThat(GuiLayout.fiveWideRowsForPage(5, 0)).isEqualTo(2);
    assertThat(GuiLayout.fiveWideRowsForPage(6, 0)).isEqualTo(3);
    assertThat(GuiLayout.fiveWideRowsForPage(25, 0)).isEqualTo(6);
    assertThat(GuiLayout.fiveWideRowsForPage(1, 6)).isEqualTo(6);
    assertThat(GuiLayout.fiveWideRowsForPage(25, 3)).isEqualTo(3);
    assertThat(GuiLayout.fiveWideRowsForPage(25, 1)).isEqualTo(2);
    assertThat(GuiLayout.fiveWideRowsForPage(25, 12)).isEqualTo(6);
  }

  @Test
  void fifteenMutationCardsFitThreeRowsWithoutCollisions() {
    Set<String> coordinates = new HashSet<>();
    for (int index = 0; index < 15; index++) {
      int row = 2 + (index / 5);
      int position = GuiLayout.spacedFivePosition(index % 5, 5);
      coordinates.add(position + ":" + row);
    }

    assertThat(coordinates).hasSize(15);
    assertThat(coordinates).allMatch(coordinate -> coordinate.endsWith(":2")
        || coordinate.endsWith(":3")
        || coordinate.endsWith(":4"));
  }

  private int[] positions(int count) {
    int[] positions = new int[count];
    for (int index = 0; index < count; index++) {
      positions[index] = GuiLayout.spacedFivePosition(index, count);
    }
    return positions;
  }
}
