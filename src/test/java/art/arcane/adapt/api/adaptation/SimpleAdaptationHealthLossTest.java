package art.arcane.adapt.api.adaptation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleAdaptationHealthLossTest {
  @Test
  void directHealthLossClampsWithoutDamageSemantics() {
    assertThat(SimpleAdaptation.healthAfterLoss(20D, 5D)).isEqualTo(15D);
    assertThat(SimpleAdaptation.healthAfterLoss(4D, 10D)).isZero();
    assertThat(SimpleAdaptation.healthAfterLoss(4D, -1D)).isEqualTo(4D);
    assertThat(SimpleAdaptation.healthAfterLoss(Double.NaN, 1D)).isZero();
  }
}
