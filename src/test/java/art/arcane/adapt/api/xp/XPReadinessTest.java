package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class XPReadinessTest {
  @Test
  void missingProfilesAreNoOps() {
    assertThatCode(() -> {
      XP.xp((AdaptPlayer) null, null, 1D);
      XP.xpSilent((AdaptPlayer) null, null, 1D);
      XP.wisdom((AdaptPlayer) null, 1L);
      XP.knowledge((AdaptPlayer) null, null, 1L);
      XP.boostXP((AdaptPlayer) null, null, 1D, 1L);
      XP.xp((Player) null, null, 1D);
      XP.xpSilent((Player) null, null, 1D);
      XP.wisdom((Player) null, 1L);
      XP.knowledge((Player) null, null, 1L);
      XP.boostXP((Player) null, null, 1D, 1L);
    }).doesNotThrowAnyException();
  }

  @Test
  void unreadyProfilesCannotReceiveProgression() {
    AdaptPlayer player = mock(AdaptPlayer.class);
    Skill<?> skill = mock(Skill.class);
    when(player.isRuntimeReady()).thenReturn(false);

    XP.xp(player, skill, 1D);
    XP.xpSilent(player, skill, 1D);
    XP.wisdom(player, 1L);
    XP.knowledge(player, skill, 1L);
    XP.boostXP(player, skill, 1D, 1L);

    verify(player, times(5)).isRuntimeReady();
    verifyNoMoreInteractions(player);
    verifyNoInteractions(skill);
  }

  @Test
  void readyProfilesRetainKnowledgeAndBoostProgression() {
    AdaptPlayer player = mock(AdaptPlayer.class);
    Skill<?> skill = mock(Skill.class);
    PlayerSkillLine skillLine = mock(PlayerSkillLine.class);
    when(player.isRuntimeReady()).thenReturn(true);
    when(skill.getName()).thenReturn("agility");
    when(player.getSkillLine("agility")).thenReturn(skillLine);

    XP.knowledge(player, skill, 3L);
    XP.boostXP(player, skill, 0.25D, 1_000L);

    verify(skillLine).giveKnowledge(3L);
    verify(skillLine).boost(0.25D, 1_000L);
  }
}
