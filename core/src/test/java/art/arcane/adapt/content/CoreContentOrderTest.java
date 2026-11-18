package art.arcane.adapt.content;

import art.arcane.adapt.api.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreContentOrderTest {
  @Test
  void skillsRegisterInThePrePortOrderWithoutDuplicates() {
    List<String> names = new ArrayList<>();
    for (Class<? extends Skill<?>> type : CoreContent.skillTypes()) {
      names.add(type.getSimpleName());
    }

    assertThat(names).containsExactly(
        "SkillAgility", "SkillArchitect", "SkillAxes", "SkillBlocking", "SkillChronos", "SkillCrafting",
        "SkillDiscovery", "SkillEnchanting", "SkillHerbalism", "SkillHunter", "SkillPickaxes", "SkillRanged",
        "SkillRift", "SkillSeaborne", "SkillStealth", "SkillSwords", "SkillTaming", "SkillTragOul", "SkillUnarmed",
        "SkillExcavation", "SkillBrewing", "SkillNether", "SkillKinetics", "SkillGolemancy");
  }
}
