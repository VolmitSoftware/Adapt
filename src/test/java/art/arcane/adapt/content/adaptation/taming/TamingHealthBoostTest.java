package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TamingHealthBoostTest {
  @Test
  void firstActiveSampleDoesNotPreCreditTime() {
    assertThat(TamingHealthBoost.elapsedActiveTicks(null, 4753L)).isZero();
  }

  @Test
  void laterActiveSamplesUseActualElapsedTime() {
    assertThat(TamingHealthBoost.elapsedActiveTicks(1000L, 5753L)).isCloseTo(95.06D, offset(0.0001D));
  }
}
