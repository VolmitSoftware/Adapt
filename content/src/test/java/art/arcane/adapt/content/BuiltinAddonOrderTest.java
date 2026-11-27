package art.arcane.adapt.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinAddonOrderTest {
  @Test
  void skillsRegisterInThePrePortOrderWithoutDuplicates() {
    List<String> names = new ArrayList<>();
    for (BuiltinAddon.Entry skill : BuiltinAddon.skills()) {
      names.add(skill.type().getSimpleName());
    }

    assertThat(names).containsExactly(
        "SkillAgility", "SkillArchitect", "SkillAxes", "SkillBlocking", "SkillChronos", "SkillCrafting",
        "SkillDiscovery", "SkillEnchanting", "SkillHerbalism", "SkillHunter", "SkillPickaxes", "SkillRanged",
        "SkillRift", "SkillSeaborne", "SkillStealth", "SkillSwords", "SkillTaming", "SkillTragOul", "SkillUnarmed",
        "SkillExcavation", "SkillBrewing", "SkillNether", "SkillKinetics", "SkillGolemancy");
  }
}
