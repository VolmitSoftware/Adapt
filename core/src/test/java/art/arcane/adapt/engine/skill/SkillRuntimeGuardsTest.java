package art.arcane.adapt.engine.skill;

import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillRuntimeGuardsTest {
  @Test
  void runtimePlayersRequireTheCurrentReadyProfile() {
    PlayerRef player = mock(PlayerRef.class);
    PlayerRef otherPlayer = mock(PlayerRef.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(player.isOnline()).thenReturn(true);
    when(otherPlayer.isOnline()).thenReturn(true);

    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(player, adaptPlayer)).isTrue();
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(otherPlayer, adaptPlayer)).isFalse();
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(player, null)).isFalse();
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(null, adaptPlayer)).isFalse();

    when(player.isOnline()).thenReturn(false);
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(player, adaptPlayer)).isFalse();
    when(player.isOnline()).thenReturn(true);

    when(adaptPlayer.isRuntimeReady()).thenReturn(false);
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(player, adaptPlayer)).isFalse();
  }

  @Test
  void xpGrantsRequirePositiveFiniteAmounts() {
    assertThat(SkillRuntimeGuards.isValidXp(1D)).isTrue();
    assertThat(SkillRuntimeGuards.isValidXp(Double.MIN_VALUE)).isTrue();
    assertThat(SkillRuntimeGuards.isValidXp(0D)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(-1D)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(Double.NaN)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(Double.NEGATIVE_INFINITY)).isFalse();
  }
}
