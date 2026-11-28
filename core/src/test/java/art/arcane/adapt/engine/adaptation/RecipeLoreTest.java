package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.gui.LoreLists;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.List;

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
  void theLevelThatUnlocksRecipesNamesEveryRecipeAndLeavesToolsOut() {
    Adaptation<?> apothecary = registry.getAdaptation(Id.adapt("brewing/upgrade/apothecary"));
    List<String> brewed = AdaptationGuiSupport.recipeNames(apothecary, 1);
    assertThat(brewed).hasSize(11).startsWith("Bottled Absorption", "Bottled Absorption 2", "Bottled Life");
    assertThat(LoreLists.joined(brewed)).endsWith(", and 3 more");
    assertThat(AdaptationGuiSupport.recipeNames(apothecary, 2)).isEmpty();
    Adaptation<?> chalk = registry.getAdaptation(Id.adapt("architect/chalk_line"));
    assertThat(AdaptationGuiSupport.recipeNames(chalk, 2)).containsExactly("Chalk Polyline Wand");
    assertThat(AdaptationGuiSupport.recipeNames(registry.getAdaptation(Id.adapt("agility/wall_jump")), 1)).isEmpty();
    assertThat(AdaptationGuiSupport.recipeNames(registry.getAdaptation(Id.adapt("stealth/upgrade/phantom_decoy")), 1)).isEmpty();
  }
}
