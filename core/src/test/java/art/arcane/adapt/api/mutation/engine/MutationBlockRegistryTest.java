package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.MutationOverrides;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every skill that has started its blocks (its {@code blocks} package exists or it registers one) brings all six, each
 * following the conventions; skills that have not started are reported as skipped. Runs for each skill lane without
 * edits.
 */
class MutationBlockRegistryTest {
  private static final Path CONTENT = Path.of(System.getProperty("user.dir"))
      .resolve("src/main/java/art/arcane/adapt/content/adaptation");

  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;

  @BeforeAll
  static void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @TestFactory
  Stream<DynamicTest> everyStartedSkillHasAllSixBlocks() {
    return SkillBlockCatalog.all().stream().map((SkillBlocks catalog) -> DynamicTest.dynamicTest(catalog.skillId(),
        () -> {
          Skill<?> skill = registry.getSkill(catalog.skillId());
          Assumptions.assumeTrue(started(catalog.skillId(), skill), catalog.skillId() + " has no blocks yet");
          assertThat(skill.getUnavailableBlocks()).as("blocks %s cannot run on 26.3", catalog.skillId()).isEmpty();
          assertThat(skill.getBlocks()).extracting(MutationBlock::getSlot).as("slots of %s", catalog.skillId())
              .containsExactlyInAnyOrder(BlockSlot.values());
          for (MutationBlock<?> block : skill.getBlocks()) {
            BlockSlot slot = block.getSlot();
            assertThat(block.getName()).isEqualTo(MutationBlocks.id(catalog.skillId(), slot));
            assertThat(block.getClass().getName()).isEqualTo(MutationBlocks.className(catalog.skillId(), slot));
            assertThat(block).isInstanceOf(baseOf(slot));
            assertThat(block.getConfigurationClass()).as("config of %s", block.getName()).isNotNull();
            assertThat(MutationBlock.Config.class).isAssignableFrom(block.getConfigurationClass());
            assertThat(block.getConfig()).isNotNull();
            assertThat(C.stripColor(block.getDisplayName()))
                .isEqualTo(catalog.skillName() + " " + MutatedText.blockName(MutatedText.ENGLISH, block.getRef())
                    .substring(catalog.skillName().length() + 1));
            assertThat(block.getDescription()).isEqualTo(catalog.text(slot));
          }
        }));
  }

  @Test
  void aStateAskedForARecentWindowRemembersOne() throws NoSuchMethodException {
    for (MutationOverride override : MutationOverrides.all()) {
      if (override.stateWindowSeconds() <= 0) {
        continue;
      }
      MutationBlock<?> state = MutationBlocks.find(registry.getSkill(override.partnerId()), BlockSlot.STATE);
      if (state != null) {
        assertThat(state.getClass().getMethod("heldWithin", PlayerRef.class, long.class).getDeclaringClass())
            .as("%s must override heldWithin for %s + %s", state.getClass().getSimpleName(), override.leadId(),
                override.partnerId())
            .isNotEqualTo(StateBlock.class);
      }
    }
  }

  @Test
  void everyPairOfStartedSkillsHasAllFiveMutatedAdaptations() {
    List<String> started = new ArrayList<>();
    for (SkillBlocks catalog : SkillBlockCatalog.all()) {
      if (started(catalog.skillId(), registry.getSkill(catalog.skillId()))) {
        started.add(catalog.skillId());
      }
    }
    for (String lead : started) {
      for (String partner : started) {
        if (lead.equals(partner)) {
          continue;
        }
        MutatedSkill mutated = registry.getMutatedSkill(new SkillMutation(lead, partner).id());
        assertThat(mutated.getMutationAdaptations()).extracting(MutationAdaptation::getRecipe)
            .as("%s + %s", lead, partner).containsExactly(Recipe.values());
        for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
          assertThat(adaptation.getBlocks()).extracting(MutationBlock::getRef)
              .isEqualTo(adaptation.getRecipeData().blocks());
          assertThat(adaptation.getDescription()).isEqualTo(adaptation.getRecipeData().description());
          assertThat(C.stripColor(adaptation.getDisplayName())).isEqualTo(adaptation.getRecipeData().name());
        }
      }
    }
  }

  private static boolean started(String skillId, Skill<?> skill) {
    return Files.isDirectory(CONTENT.resolve(SkillUpgrades.contentPackage(skillId)).resolve("blocks"))
        || (skill != null && (!skill.getBlocks().isEmpty() || !skill.getUnavailableBlocks().isEmpty()));
  }

  private static Class<?> baseOf(BlockSlot slot) {
    return switch (slot) {
      case TRIGGER -> TriggerBlock.class;
      case STRIKE_FIGHT, STRIKE_WORK -> StrikeBlock.class;
      case BOON -> BoonBlock.class;
      case FUEL -> FuelBlock.class;
      case STATE -> StateBlock.class;
    };
  }
}
