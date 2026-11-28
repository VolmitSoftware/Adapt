package art.arcane.adapt.util.common.inventorygui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuiLayoutTest {
  @Test
  void everySupportedRowIsCenteredWithoutGaps() {
    for (int count = 1; count <= GuiLayout.WIDTH; count++) {
      int first = GuiLayout.centeredPosition(0, count);
      int last = GuiLayout.centeredPosition(count - 1, count);
      for (int index = 0; index < count; index++) {
        assertThat(GuiLayout.centeredPosition(index, count)).isEqualTo(first + index);
      }
      assertThat(first).isGreaterThanOrEqualTo(-4);
      assertThat(last).isLessThanOrEqualTo(4);
      assertThat((first + 4) - (4 - last)).isBetween(-1, 0);
    }
  }
}
