package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationScalingSafetyTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  private static TestAdaptation adaptation() {
    return TestContent.adaptation(Id.adapt("scaling"), TestAdaptation::new);
  }

  @Test
  void sharedScalingInputsAreNormalizedToUsableDomains() {
    TestAdaptation adaptation = adaptation();

    adaptation.getConfig().maxLevel = 0;
    adaptation.getConfig().baseCost = -10;
    adaptation.getConfig().initialCost = -5;
    adaptation.getConfig().costFactor = Double.NaN;

    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getBaseCost()).isZero();
    assertThat(adaptation.getInitialCost()).isZero();
    assertThat(adaptation.getCostFactor()).isZero();
    assertThat(adaptation.getLevelPercent(1)).isEqualTo(1D);
    assertThat(adaptation.getCostFor(1)).isEqualTo(1);
  }

  @Test
  void individualAndCumulativeCostsSaturateInsteadOfOverflowing() {
    TestAdaptation adaptation = adaptation();
    adaptation.getConfig().baseCost = Integer.MAX_VALUE;
    adaptation.getConfig().initialCost = Integer.MAX_VALUE;
    adaptation.getConfig().costFactor = Double.MAX_VALUE;

    assertThat(adaptation.getCostFor(1)).isEqualTo(Integer.MAX_VALUE);
    assertThat(adaptation.getCostFor(5, 0)).isEqualTo(Integer.MAX_VALUE);
    assertThat(AdaptationHost.refundCostFor(adaptation, 0, 5)).isEqualTo(Integer.MAX_VALUE);
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super(Id.adapt("scaling-safety"));
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines element) {
    }
  }
}
