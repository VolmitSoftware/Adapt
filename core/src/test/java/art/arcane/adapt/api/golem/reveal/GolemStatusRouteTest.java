package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.GolemNameMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemStatusRouteTest {
  @Test
  void hiddenStatusLabelsHideTheStatusInEveryMode() {
    for (GolemNameMode mode : GolemNameMode.values()) {
      assertThat(GolemStatusRoute.of(mode, false)).isEqualTo(GolemStatusRoute.HIDDEN);
    }
  }

  @Test
  void glossCarriesTheStatus() {
    assertThat(GolemStatusRoute.of(GolemNameMode.GLOSS, true)).isEqualTo(GolemStatusRoute.GLOSS);
  }

  @Test
  void vanillaKeepsTheFloatingLabel() {
    assertThat(GolemStatusRoute.of(GolemNameMode.VANILLA, true)).isEqualTo(GolemStatusRoute.LABEL);
  }
}
