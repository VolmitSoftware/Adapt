package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TamingStableHandTest {
  @Test
  void biasGrowsWithLevelAndFavorsHigherLevels() {
    assertThat(TamingStableHand.bias(0.0, 0.1, 0.2, 0.3)).isCloseTo(0.1, offset(1.0E-9));
    assertThat(TamingStableHand.bias(0.5, 0.1, 0.2, 0.3)).isCloseTo(0.2, offset(1.0E-9));
    assertThat(TamingStableHand.bias(1.0, 0.1, 0.2, 0.3)).isCloseTo(0.3, offset(1.0E-9));
  }

  @Test
  void biasIsClampedToTheConfiguredMaximum() {
    assertThat(TamingStableHand.bias(1.0, 0.2, 0.5, 0.4)).isCloseTo(0.4, offset(1.0E-9));
  }
}
