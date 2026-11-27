package art.arcane.adapt.engine.world;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.skill.SkillRegistry;
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
    lenient().when(skill.getId()).thenReturn(Id.parseAdapt(skillName));
    lenient().when(skill.isEnabled()).thenReturn(true);
    KList<Adaptation<?>> adaptations = new KList<>();
    for (String id : adaptationIds) {
      Adaptation<?> adaptation = mock(Adaptation.class);
      lenient().when(adaptation.getId()).thenReturn(Id.parseAdapt(id));
      lenient().when(adaptation.isEnabled()).thenReturn(true);
      lenient().doReturn(skill).when(adaptation).getSkill();
      adaptations.add(adaptation);
    }
    lenient().when(skill.getAdaptations()).thenReturn(adaptations);
    SkillRegistry registry = mock(SkillRegistry.class);
    lenient().doReturn(skill).when(registry).getSkill(skillName);
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt(skillName).toString());
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt(skillName));
    AdaptServer server = mock(AdaptServer.class);
    lenient().when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(server).when(core).getAdaptServer();
  }
}
