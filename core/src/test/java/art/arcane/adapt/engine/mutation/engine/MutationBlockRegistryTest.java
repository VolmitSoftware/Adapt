package art.arcane.adapt.engine.mutation.engine;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillHost;

import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.content.BuiltinAddon;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every skill that has started its blocks (its {@code blocks} package exists or it registers one) brings all six, each
 * following the conventions; skills that have not started are reported as skipped. Runs for each skill lane without
 * edits.
 */
class MutationBlockRegistryTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;

  @BeforeAll
  static void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @TestFactory
  Stream<DynamicTest> everyStartedSkillHasAllSixBlocks() {
    return registry.getSkills().stream().map((Skill<?> skill) -> DynamicTest.dynamicTest(skill.getId().toString(),
        () -> {
          Assumptions.assumeTrue(started(skill), skill.getId() + " has no blocks yet");
          assertThat(SkillHost.of(skill).getUnavailableBlocks()).as("blocks %s cannot run on 26.3", skill.getId()).isEmpty();
          assertThat(SkillHost.of(skill).getBlocks()).extracting(MutationBlock::getSlot).as("slots of %s", skill.getId())
              .containsExactlyInAnyOrder(BlockSlot.values());
          for (MutationBlock<?> block : SkillHost.of(skill).getBlocks()) {
            BlockSlot slot = block.getSlot();
            assertThat(block.getId()).isEqualTo(new BlockKey(skill.getId(), slot).id());
            assertThat(block.getClass().getName())
                .matches("art\\.arcane\\.adapt\\.content\\.adaptation\\.[a-z]+\\.blocks\\.[A-Za-z]+" + classSuffix(slot));
            assertThat(block).isInstanceOf(baseOf(slot));
            assertThat(block.getConfigurationClass()).as("config of %s", block.getId().toString()).isNotNull();
            assertThat(MutationBlock.Config.class).isAssignableFrom(block.getConfigurationClass());
            assertThat(block.getConfig()).isNotNull();
            assertThat(C.stripColor(block.getDisplayName()))
                .isEqualTo(MutatedText.blockName(MutatedText.ENGLISH, skill, slot));
            assertThat(block.getDescription()).isEqualTo(MutatedText.phrase(MutatedText.ENGLISH, skill, slot));
          }
        }));
  }

  @Test
  void aStateAskedForARecentWindowRemembersOne() throws NoSuchMethodException {
    PairRegistry claims = registry.getPairRegistry();
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead == partner) {
          continue;
        }
        for (Recipe recipe : Recipe.values()) {
          Optional<PairRegistry.Claim<PairText>> text =
              claims.text(new MutationSlot(new SkillPair(lead.getId(), partner.getId()), recipe));
          if (text.isEmpty() || text.get().value().stateWindowSeconds() <= 0) {
            continue;
          }
          MutationBlock<?> state = MutationBlocks.find(partner, BlockSlot.STATE);
          if (state != null) {
            assertThat(state.getClass().getMethod("heldWithin", PlayerRef.class, long.class).getDeclaringClass())
                .as("%s must override heldWithin for %s + %s", state.getClass().getSimpleName(), lead.getId(),
                    partner.getId())
                .isNotEqualTo(StateBlock.class);
          }
        }
      }
    }
  }

  @Test
  void everyPairOfStartedSkillsHasAllFiveMutatedAdaptations() {
    List<Skill<?>> started = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      if (started(skill)) {
        started.add(skill);
      }
    }
    for (Skill<?> lead : started) {
      for (Skill<?> partner : started) {
        if (lead == partner) {
          continue;
        }
        MutatedSkill mutated = registry.getMutatedSkill(new SkillPair(lead.getId(), partner.getId()));
        assertThat(mutated.getMutationAdaptations()).extracting(MutationAdaptation::getRecipe)
            .as("%s + %s", lead.getId(), partner.getId()).containsExactly(Recipe.values());
        for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
          assertThat(adaptation.getBlocks()).extracting(MutationBlock::getKey)
              .isEqualTo(adaptation.getRecipeData().blocks());
          assertThat(adaptation.getDescription())
              .isEqualTo(MutatedText.description(MutatedText.ENGLISH, mutated.parents(), adaptation.getRecipe()));
          assertThat(C.stripColor(adaptation.getDisplayName()))
              .isEqualTo(MutatedText.name(MutatedText.ENGLISH, mutated.parents(), adaptation.getRecipe()));
        }
      }
    }
  }

  private static boolean started(Skill<?> skill) {
    return !SkillHost.of(skill).getBlocks().isEmpty() || !SkillHost.of(skill).getUnavailableBlocks().isEmpty();
  }

  private static String classSuffix(BlockSlot slot) {
    return switch (slot) {
      case TRIGGER -> "Trigger";
      case STRIKE_FIGHT -> "FightStrike";
      case STRIKE_WORK -> "WorkStrike";
      case BOON -> "Boon";
      case FUEL -> "Fuel";
      case STATE -> "State";
    };
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
