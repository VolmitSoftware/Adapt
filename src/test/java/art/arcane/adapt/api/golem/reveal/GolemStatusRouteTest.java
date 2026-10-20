package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.GolemNameMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemStatusRouteTest {
  @Test
  void hiddenStatusLabelsHideTheStatusInEveryMode() {
    for (GolemNameMode mode : GolemNameMode.values()) {
      assertThat(GolemStatusRoute.of(mode, false, true)).isEqualTo(GolemStatusRoute.HIDDEN);
      assertThat(GolemStatusRoute.of(mode, false, false)).isEqualTo(GolemStatusRoute.HIDDEN);
    }
  }

  @Test
  void glossCarriesTheStatusWhetherOrNotTheOwnerHasInsight() {
    assertThat(GolemStatusRoute.of(GolemNameMode.GLOSS, true, true)).isEqualTo(GolemStatusRoute.GLOSS);
    assertThat(GolemStatusRoute.of(GolemNameMode.GLOSS, true, false)).isEqualTo(GolemStatusRoute.GLOSS);
  }

  @Test
  void insightCarriesTheStatusOnlyForAnOwnerWithInsightAndOthersFallBackToTheLabel() {
    assertThat(GolemStatusRoute.of(GolemNameMode.INSIGHT, true, true)).isEqualTo(GolemStatusRoute.INSIGHT);
    assertThat(GolemStatusRoute.of(GolemNameMode.INSIGHT, true, false)).isEqualTo(GolemStatusRoute.LABEL);
  }

  @Test
  void vanillaKeepsTheFloatingLabel() {
    assertThat(GolemStatusRoute.of(GolemNameMode.VANILLA, true, false)).isEqualTo(GolemStatusRoute.LABEL);
    assertThat(GolemStatusRoute.of(GolemNameMode.VANILLA, true, true)).isEqualTo(GolemStatusRoute.LABEL);
  }
}
