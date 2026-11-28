package art.arcane.adapt.pack.hud;

import art.arcane.adapt.AdaptConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HudPlacementTest {
  @Test
  void keysIgnoreCaseAndSpaceAndUnknownKeysAreNull() {
    assertThat(HudPlacement.of(" Hotbar ")).isEqualTo(HudPlacement.HOTBAR);
    assertThat(HudPlacement.of("corner")).isEqualTo(HudPlacement.CORNER);
    assertThat(HudPlacement.of("off")).isEqualTo(HudPlacement.OFF);
    assertThat(HudPlacement.of("left")).isNull();
    assertThat(HudPlacement.of(null)).isNull();
  }

  @Test
  void theDefaultConfigPutsTheHudInTheCorner() {
    assertThat(new AdaptConfig().getPack().hudPlacement()).isEqualTo(HudPlacement.CORNER);
  }
}
