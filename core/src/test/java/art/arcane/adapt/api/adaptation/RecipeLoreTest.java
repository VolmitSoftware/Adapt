package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.content.CoreContent;
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
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void theLevelThatUnlocksRecipesNamesThreeAndCountsTheRest() {
    Adaptation<?> apothecary = registry.getAdaptation("upgraded-brewing-apothecary");
    assertThat(C.stripColor(AdaptationGuiSupport.recipeLore(apothecary, 1)))
        .isEqualTo("Recipes: Bottled Absorption, Bottled Absorption 2, Bottled Life +8");
    assertThat(AdaptationGuiSupport.recipeLore(apothecary, 2)).isEmpty();
    Adaptation<?> chalk = registry.getAdaptation("architect-chalk-line");
    assertThat(C.stripColor(AdaptationGuiSupport.recipeLore(chalk, 2))).isEqualTo("Recipes: Chalk Polyline Wand");
    assertThat(AdaptationGuiSupport.recipeLore(registry.getAdaptation("agility-wall-jump"), 1)).isEmpty();
    assertThat(AdaptationGuiSupport.recipeLore(registry.getAdaptation("upgraded-stealth-phantom-decoy"), 1)).isEmpty();
  }
}
