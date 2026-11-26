package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.volmlib.util.collection.KList;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Serves one enabled skill with enabled adaptations from the core's skill registry. */
final class RegisteredSkills {
  private RegisteredSkills() {
  }

  static void register(AdaptCore core, String skillName, String... adaptationIds) {
    Skill<?> skill = mock(Skill.class);
    lenient().when(skill.getName()).thenReturn(skillName);
    lenient().when(skill.isEnabled()).thenReturn(true);
    KList<Adaptation<?>> adaptations = new KList<>();
    for (String id : adaptationIds) {
      Adaptation<?> adaptation = mock(Adaptation.class);
      lenient().when(adaptation.getName()).thenReturn(id);
      lenient().when(adaptation.isEnabled()).thenReturn(true);
      lenient().doReturn(skill).when(adaptation).getSkill();
      adaptations.add(adaptation);
    }
    lenient().when(skill.getAdaptations()).thenReturn(adaptations);
    SkillRegistry registry = mock(SkillRegistry.class);
    lenient().doReturn(skill).when(registry).getSkill(skillName);
    AdaptServer server = mock(AdaptServer.class);
    lenient().when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(server).when(core).getAdaptServer();
  }
}
