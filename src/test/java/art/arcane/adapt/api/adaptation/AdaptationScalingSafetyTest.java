package art.arcane.adapt.api.adaptation;

import art.arcane.volmlib.util.inventorygui.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationScalingSafetyTest {
  @Test
  void sharedScalingInputsAreNormalizedToUsableDomains() {
    TestAdaptation adaptation = new TestAdaptation();

    adaptation.setMaxLevel(0);
    adaptation.setBaseCost(-10);
    adaptation.setInitialCost(-5);
    adaptation.setCostFactor(Double.NaN);

    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getBaseCost()).isZero();
    assertThat(adaptation.getInitialCost()).isZero();
    assertThat(adaptation.getCostFactor()).isZero();
    assertThat(adaptation.getLevelPercent(1)).isEqualTo(1D);
    assertThat(adaptation.getCostFor(1)).isEqualTo(1);
  }

  @Test
  void individualAndCumulativeCostsSaturateInsteadOfOverflowing() {
    TestAdaptation adaptation = new TestAdaptation();
    adaptation.setBaseCost(Integer.MAX_VALUE);
    adaptation.setInitialCost(Integer.MAX_VALUE);
    adaptation.setCostFactor(Double.MAX_VALUE);

    assertThat(adaptation.getCostFor(1)).isEqualTo(Integer.MAX_VALUE);
    assertThat(adaptation.getCostFor(5, 0)).isEqualTo(Integer.MAX_VALUE);
    assertThat(adaptation.getRefundCostFor(0, 5)).isEqualTo(Integer.MAX_VALUE);
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super("scaling-safety");
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, Element element) {
    }
  }
}
