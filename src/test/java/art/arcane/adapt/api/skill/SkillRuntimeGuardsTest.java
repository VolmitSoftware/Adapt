package art.arcane.adapt.api.skill;

import art.arcane.adapt.api.world.AdaptPlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillRuntimeGuardsTest {
  @Test
  void runtimePlayersRequireTheCurrentReadyProfile() {
    Player player = mock(Player.class);
    Player otherPlayer = mock(Player.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);

    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(player, adaptPlayer)).isTrue();
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(otherPlayer, adaptPlayer)).isFalse();
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(player, null)).isFalse();
    assertThat(SkillRuntimeGuards.isReadyRuntimePlayer(null, adaptPlayer)).isFalse();

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
