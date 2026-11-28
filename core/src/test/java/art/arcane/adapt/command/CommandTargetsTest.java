package art.arcane.adapt.command;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandTargetsTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;

  @BeforeAll
  static void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void aMutatedAdaptationIsFoundBeforeItsSkillIsBuilt() {
    SkillPair pair = new SkillPair(Id.parseAdapt("axes"), Id.parseAdapt("swords"));
    Id graft = pair.child("graft");

    Adaptation<?> found = CommandTargets.findAdaptation(graft);

    assertThat(found).isNotNull();
    assertThat(found.getId()).isEqualTo(graft);
  }

  @Test
  void everyPartOfAMutatedSkillIsFound() {
    MutatedSkill mutated = registry.getMutatedSkill(new SkillPair(Id.parseAdapt("swords"), Id.parseAdapt("stealth")));
    List<Adaptation<?>> parts = mutated.switchedAdaptations();

    assertThat(parts).isNotEmpty();
    for (Adaptation<?> part : parts) {
      assertThat(CommandTargets.findAdaptation(part.getId())).describedAs(part.getId().toString()).isSameAs(part);
    }
  }

  @Test
  void aNameBelowNoMutationIsUnknown() {
    assertThat(CommandTargets.findAdaptation(Id.parseAdapt("mutated/axes/adapt/axes/graft"))).isNull();
    assertThat(CommandTargets.findAdaptation(Id.parseAdapt("mutated/axes/adapt/nothing/graft"))).isNull();
    assertThat(CommandTargets.findAdaptation(Id.parseAdapt("mutated/axes"))).isNull();
  }

  @Test
  void completionOffersTheBuiltMutatedAdaptationsAndUpgrades() {
    MutatedSkill mutated = registry.getMutatedSkill(new SkillPair(Id.parseAdapt("hunter"), Id.parseAdapt("ranged")));
    List<String> offered = AdaptationListingHandler.getAdaptationProviders().stream()
        .map(AdaptationListingHandler.AdaptationProvider::name).toList();

    for (Adaptation<?> part : mutated.builtParts()) {
      if (part.isEnabled()) {
        assertThat(offered).contains(part.getId().toString());
      }
    }
    for (Adaptation<?> upgrade : mutated.combinedAdaptations()) {
      if (upgrade.isEnabled()) {
        assertThat(offered).contains(upgrade.getId().toString());
      }
    }
  }
}
