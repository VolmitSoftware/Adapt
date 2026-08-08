package art.arcane.adapt.content.event;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptAdaptationEventTest {
  @Test
  @SuppressWarnings("unchecked")
  void resolvesPlayerSkillByRegistryName() {
    AdaptPlayer player = mock(AdaptPlayer.class);
    Adaptation<Object> adaptation = mock(Adaptation.class);
    Skill<Object> skill = mock(Skill.class);
    PlayerSkillLine playerSkill = mock(PlayerSkillLine.class);
    doReturn(skill).when(adaptation).getSkill();
    when(skill.getName()).thenReturn("stealth");
    when(skill.getId()).thenReturn("ticker-id-that-is-not-the-registry-name");
    when(player.getSkillLine("stealth")).thenReturn(playerSkill);

    AdaptAdaptationEvent event = new AdaptAdaptationEvent(false, player, adaptation);

    assertThat(event.getSkill()).isSameAs(skill);
    assertThat(event.getAdaptation()).isSameAs(adaptation);
    assertThat(event.getPlayerSkill()).isSameAs(playerSkill);
    verify(player).getSkillLine("stealth");
  }
}
