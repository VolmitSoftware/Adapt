package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.Skill;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAdaptationRowGroupsTest {
  @Test
  void skillsDeclareNoRowGroupsByDefault() {
    Skill<?> skill = Mockito.mock(Skill.class);

    assertThat(SkillHost.adaptationRowGroups(skill)).isEmpty();
  }
}
