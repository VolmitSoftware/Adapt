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
    assertThat(GuiLayout.centeredFiveRow(0, 1)).isEqualTo(2);
    assertThat(GuiLayout.centeredFiveRow(0, 2)).isEqualTo(1);
    assertThat(GuiLayout.centeredFiveRow(1, 2)).isEqualTo(3);
    assertThat(GuiLayout.centeredFiveRow(0, 4)).isEqualTo(0);
    assertThat(GuiLayout.centeredFiveRow(2, 4)).isEqualTo(3);
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
