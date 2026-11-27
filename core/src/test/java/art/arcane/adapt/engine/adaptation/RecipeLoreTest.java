package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** A chest menu's level item names what that level's recipes make, briefly, and leaves tools to the tools line. */
class RecipeLoreTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;

  @BeforeAll
  static void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void theLevelThatUnlocksRecipesNamesThreeAndCountsTheRest() {
    Adaptation<?> apothecary = registry.getAdaptation(Id.adapt("brewing/upgrade/apothecary"));
    assertThat(C.stripColor(AdaptationGuiSupport.recipeLore(apothecary, 1)))
        .isEqualTo("Recipes: Bottled Absorption, Bottled Absorption 2, Bottled Life +8");
    assertThat(AdaptationGuiSupport.recipeLore(apothecary, 2)).isEmpty();
    Adaptation<?> chalk = registry.getAdaptation(Id.adapt("architect/chalk_line"));
    assertThat(C.stripColor(AdaptationGuiSupport.recipeLore(chalk, 2))).isEqualTo("Recipes: Chalk Polyline Wand");
    assertThat(AdaptationGuiSupport.recipeLore(registry.getAdaptation(Id.adapt("agility/wall_jump")), 1)).isEmpty();
    assertThat(AdaptationGuiSupport.recipeLore(registry.getAdaptation(Id.adapt("stealth/upgrade/phantom_decoy")), 1)).isEmpty();
  }
}
