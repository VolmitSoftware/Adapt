package art.arcane.adapt.command;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.testkit.MockedHosts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandAdaptGuiForceTest {
  private final MockedHosts hosts = new MockedHosts();

  @AfterEach
  void closeHosts() {
    hosts.close();
  }

  @Test
  void forcedSkillGuiStillOpensAndSkipsOnlyThePermissionCheck() {
    Skill<?> skill = mock(Skill.class);
    SkillHost host = hosts.skill(skill);
    PlayerRef player = player();
    when(host.openGui(player, false)).thenReturn(true);

    assertThat(CommandAdapt.openSkillGui(skill, player, true)).isTrue();
    verify(host).openGui(player, false);
  }

  @Test
  void forcedAdaptationGuiStillOpensAndSkipsOnlyThePermissionCheck() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    AdaptationHost host = hosts.adaptation(adaptation);
    PlayerRef player = player();
    when(host.openGui(player, false)).thenReturn(true);

    assertThat(CommandAdapt.openAdaptationGui(adaptation, player, true)).isTrue();
    verify(host).openGui(player, false);
  }

  @Test
  void normalGuiOpenKeepsPermissionChecksEnabled() {
    Skill<?> skill = mock(Skill.class);
    SkillHost host = hosts.skill(skill);
    PlayerRef player = player();
    when(host.openGui(player, true)).thenReturn(true);

    assertThat(CommandAdapt.openSkillGui(skill, player, false)).isTrue();
    verify(host).openGui(player, true);
  }

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    return player;
  }
}
