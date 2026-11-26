package art.arcane.adapt.api.mutation.blocks;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MutationBlocksRegistryTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), CoreContent.skillTypes());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void catalogCoversExactlyTheRegisteredSkills() {
    assertThat(SkillBlockCatalog.all()).extracting(SkillBlocks::skillId)
        .containsExactlyInAnyOrderElementsOf(adaptationNames().keySet());
  }

  @Test
  void everyFusedOriginalIsARegisteredAdaptationOfItsSkill() {
    Map<String, Set<String>> registered = adaptationNames();
    List<String> unresolved = new ArrayList<>();
    for (SkillBlocks skill : SkillBlockCatalog.all()) {
      Set<String> names = registered.getOrDefault(skill.skillId(), Set.of());
      for (UpgradedAdaptation upgrade : skill.upgrades()) {
        for (String source : upgrade.sources()) {
          if (!names.contains(source)) {
            unresolved.add(skill.skillId() + " / " + upgrade.name() + " / " + source);
          }
        }
      }
    }
    assertThat(unresolved).as("fused originals that are not registered adaptations").isEmpty();
  }

  private Map<String, Set<String>> adaptationNames() {
    Map<String, Set<String>> names = new HashMap<>();
    for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
      Set<String> skillNames = new HashSet<>();
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        skillNames.add(C.stripColor(adaptation.getDisplayName()).replaceAll("\\s+", " ").trim());
      }
      names.put(skill.getName(), skillNames);
    }
    return names;
  }
}
