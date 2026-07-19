package art.arcane.adapt.api.adaptation;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationRuntimeGuardsTest {
  @Test
  void creativeModeFollowsTheConfiguredAdaptationGate() {
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.CREATIVE, false)).isFalse();
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.CREATIVE, true)).isTrue();
  }

  @Test
  void spectatorRemainsBlockedAndGameplayModesRemainAllowed() {
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.SPECTATOR, true)).isFalse();
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.SURVIVAL, false)).isTrue();
    assertThat(AdaptationRuntimeGuards.isGameModeAllowed(GameMode.ADVENTURE, false)).isTrue();
  }
}
