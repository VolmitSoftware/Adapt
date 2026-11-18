package art.arcane.adapt.api.golem.reveal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class RevealSettingsTest {
  @Test
  void defaultsMatchThePlanBudget() {
    RevealSettings defaults = RevealSettings.defaults();

    assertThat(defaults.revealRange()).isEqualTo(32D);
    assertThat(defaults.labelRange()).isEqualTo(8D);
    assertThat(defaults.pollTicks()).isEqualTo(10);
    assertThat(defaults.pollMillis()).isEqualTo(500L);
  }

  @Test
  void labelRangeBecomesADisplayViewRangeInSixtyFourBlockUnits() {
    assertThat(new RevealSettings(32D, 8D, 10).labelViewRange()).isCloseTo(0.125F, offset(1e-6F));
    assertThat(new RevealSettings(32D, 64D, 10).labelViewRange()).isCloseTo(1F, offset(1e-6F));
  }

  @Test
  void outOfRangeValuesAreClamped() {
    RevealSettings low = new RevealSettings(-5D, 0D, 0);
    RevealSettings high = new RevealSettings(10_000D, 10_000D, 10_000);

    assertThat(low.revealRange()).isEqualTo(RevealSettings.MIN_RANGE);
    assertThat(low.labelRange()).isEqualTo(RevealSettings.MIN_RANGE);
    assertThat(low.pollTicks()).isEqualTo(RevealSettings.MIN_POLL_TICKS);
    assertThat(high.revealRange()).isEqualTo(RevealSettings.MAX_REVEAL_RANGE);
    assertThat(high.labelRange()).isEqualTo(RevealSettings.MAX_LABEL_RANGE);
    assertThat(high.pollTicks()).isEqualTo(RevealSettings.MAX_POLL_TICKS);
  }

  @Test
  void nonFiniteRangesFallBackToTheDefaults() {
    RevealSettings settings = new RevealSettings(Double.NaN, Double.POSITIVE_INFINITY, 10);

    assertThat(settings.revealRange()).isEqualTo(RevealSettings.defaults().revealRange());
    assertThat(settings.labelRange()).isEqualTo(RevealSettings.defaults().labelRange());
  }
}
