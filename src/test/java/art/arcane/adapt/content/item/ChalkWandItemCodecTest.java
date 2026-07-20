package art.arcane.adapt.content.item;

import art.arcane.adapt.content.item.ChalkWandItem.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChalkWandItemCodecTest {
  @Test
  void controlPointsRoundTripThroughCompactIntegerStorage() {
    List<Point> points = List.of(
        new Point(-12, -64, 91),
        new Point(0, 320, 0),
        new Point(30_000_000, 1, -30_000_000)
    );

    assertThat(ChalkWandItem.decodePoints(ChalkWandItem.encodePoints(points)))
        .containsExactlyElementsOf(points);
  }

  @Test
  void malformedOrUnsafePointStorageIsRejected() {
    assertThat(ChalkWandItem.decodePoints(new int[]{1, 2})).isNull();
    assertThat(ChalkWandItem.decodePoints(new int[]{30_000_001, 64, 0})).isNull();
    assertThat(ChalkWandItem.decodePoints(new int[]{0, 4_097, 0})).isNull();
  }
}
