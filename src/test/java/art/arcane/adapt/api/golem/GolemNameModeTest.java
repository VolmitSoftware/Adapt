package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemNameModeTest {
  @Test
  void anActiveGlossOverlaySelectsGloss() {
    assertThat(GolemNameMode.resolve(true)).isEqualTo(GolemNameMode.GLOSS);
  }

  @Test
  void withoutTheOverlayTheNameStaysVanilla() {
    assertThat(GolemNameMode.resolve(false)).isEqualTo(GolemNameMode.VANILLA);
  }

  @Test
  void glossAndVanillaAreTheOnlyModes() {
    assertThat(GolemNameMode.values()).containsExactly(GolemNameMode.GLOSS, GolemNameMode.VANILLA);
  }
}
