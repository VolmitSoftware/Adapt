package art.arcane.adapt.api.skill;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAdaptationRowGroupsTest {
  @Test
  void skillsDeclareNoRowGroupsByDefault() {
    Skill<?> skill = Mockito.mock(Skill.class, Mockito.CALLS_REAL_METHODS);

    assertThat(skill.getAdaptationRowGroups()).isEmpty();
  }
}
