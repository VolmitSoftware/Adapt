package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemNameModeTest {
  @Test
  void anActiveGlossOverlayWinsWhateverInsightDoes() {
    assertThat(GolemNameMode.resolve(true, true)).isEqualTo(GolemNameMode.GLOSS);
    assertThat(GolemNameMode.resolve(true, false)).isEqualTo(GolemNameMode.GLOSS);
  }

  @Test
  void insightTakesOverWithoutTheOverlayAndVanillaIsTheFallback() {
    assertThat(GolemNameMode.resolve(false, true)).isEqualTo(GolemNameMode.INSIGHT);
    assertThat(GolemNameMode.resolve(false, false)).isEqualTo(GolemNameMode.VANILLA);
  }

  @Test
  void onlyInsightModeHidesTheEntityName() {
    assertThat(GolemNameMode.GLOSS.showsCustomName()).isTrue();
    assertThat(GolemNameMode.VANILLA.showsCustomName()).isTrue();
    assertThat(GolemNameMode.INSIGHT.showsCustomName()).isFalse();
  }
}
