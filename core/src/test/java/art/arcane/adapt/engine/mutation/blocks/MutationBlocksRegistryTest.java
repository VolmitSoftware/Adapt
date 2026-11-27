package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.UpgradeDeclaration;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MutationBlocksRegistryTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void everyRegisteredSkillRegistersItsOwnKit() {
    assertThat(skills()).hasSize(24)
        .allSatisfy((Skill<?> skill) -> assertThat(skill.getMutationKit()).as(skill.getId().toString()).isPresent());
  }

  @Test
  void everyFusedOriginalIsARegisteredAdaptationOfItsSkill() {
    List<String> unresolved = new ArrayList<>();
    for (Skill<?> skill : skills()) {
      Set<Id> ids = new HashSet<>();
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        ids.add(adaptation.getId());
      }
      for (UpgradeDeclaration upgrade : skill.getMutationKit().orElseThrow().upgrades()) {
        for (Id source : upgrade.sources()) {
          if (!ids.contains(source)) {
            unresolved.add(skill.getId() + " / " + upgrade.id() + " / " + source);
          }
        }
      }
    }
    assertThat(unresolved).as("fused originals that are not registered adaptations").isEmpty();
  }

  private List<Skill<?>> skills() {
    return adapt.core().getAdaptServer().getSkillRegistry().getSkills();
  }
}
