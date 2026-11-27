package art.arcane.adapt.pack.text;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiftFontTest {
  static ShiftFont font() {
    Map<Integer, String> glyphs = new HashMap<>();
    char next = 'a';
    for (int size = 1; size <= 512; size *= 2) {
      glyphs.put(size, String.valueOf(next++));
      glyphs.put(-size, String.valueOf(Character.toUpperCase((char) (next - 1))));
    }
    return new ShiftFont("adapt:shift", glyphs);
  }

  static int advance(ShiftFont font, String text) {
    int total = 0;
    for (char c : text.toCharArray()) {
      for (Map.Entry<Integer, String> entry : font.glyphs().entrySet()) {
        if (entry.getValue().equals(String.valueOf(c))) {
          total += entry.getKey();
        }
      }
    }
    return total;
  }

  @Test
  void everyDistanceDecomposesIntoPowersOfTwoThatSumToIt() {
    ShiftFont font = font();
    for (int distance = -1500; distance <= 1500; distance++) {
      String shift = font.shift(distance);
      assertThat(advance(font, shift)).as("distance " + distance).isEqualTo(distance);
      if (Math.abs(distance) <= 1023) {
        assertThat(shift.length()).isEqualTo(Integer.bitCount(Math.abs(distance)));
      }
    }
  }

  @Test
  void largestStepsComeFirst() {
    assertThat(font().shift(13)).isEqualTo("dca");
    assertThat(font().shift(-6)).isEqualTo("CB");
    assertThat(font().shift(0)).isEmpty();
  }

  @Test
  void rejectsFontsWithoutUnitStepsOrWithOddSteps() {
    assertThatThrownBy(() -> new ShiftFont("f", Map.of(2, "a", -1, "b"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ShiftFont("f", Map.of(1, "a", -1, "b", 3, "c"))).isInstanceOf(IllegalArgumentException.class);
  }
}
