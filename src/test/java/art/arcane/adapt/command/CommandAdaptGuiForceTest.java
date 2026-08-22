package art.arcane.adapt.command;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandAdaptGuiForceTest {
  @Test
  void forcedSkillGuiStillOpensAndSkipsOnlyThePermissionCheck() {
    Skill<?> skill = mock(Skill.class);
    Player player = mock(Player.class);
    when(skill.openGui(player, false)).thenReturn(true);

    assertThat(CommandAdapt.openSkillGui(skill, player, true)).isTrue();
    verify(skill).openGui(player, false);
  }

  @Test
  void forcedAdaptationGuiStillOpensAndSkipsOnlyThePermissionCheck() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    Player player = mock(Player.class);
    when(adaptation.openGui(player, false)).thenReturn(true);

    assertThat(CommandAdapt.openAdaptationGui(adaptation, player, true)).isTrue();
    verify(adaptation).openGui(player, false);
  }

  @Test
  void normalGuiOpenKeepsPermissionChecksEnabled() {
    Skill<?> skill = mock(Skill.class);
    Player player = mock(Player.class);
    when(skill.openGui(player, true)).thenReturn(true);

    assertThat(CommandAdapt.openSkillGui(skill, player, false)).isTrue();
    verify(skill).openGui(player, true);
  }
}
