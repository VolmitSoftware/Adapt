package art.arcane.adapt.engine.mutation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.bridge.ItemTexts;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every tool has its owner, recipe gate and name, and every overcharge its label and switch. Runs for every lane without
 * edits.
 */
class MutationDepthRegistryTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static TestPlatform platform;

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void everyToolHasItsOwnerRecipeGateAndName() {
    Set<String> ids = new HashSet<>();
    for (MutationTool<?> tool : MutationTools.all(registry)) {
      Id id = tool.item().id();
      assertThat(ids.add(id.toString())).as("one tool %s", id).isTrue();
      Adaptation<?> owner = tool.owner();
      assertThat(owner).isInstanceOfAny(SkillUpgrade.class, Signature.class);
      assertThat(MutationTools.handedOut(owner)).contains(tool);
      assertThat(registry.getAdaptation(owner.getId())).isSameAs(owner);
      assertThat(tool.requiredLevel()).isBetween(1, owner.getMaxLevel());
      assertThat(ItemTexts.name(id)).as("%s name", id).isNotNull();
      assertThat(tool.recipes()).as("%s recipes", id).isNotEmpty();
      for (RecipeSpec recipe : tool.recipes()) {
        assertThat(MutationTools.isToolRecipe(recipe.key())).as(recipe.key().toString()).isTrue();
        assertThat(recipe.key().path()).startsWith(id.path());
        assertThat(tool.is(recipe.result())).as("%s makes its tool", recipe.key()).isTrue();
        assertThat(registry.getRequiredAdaptation(recipe.key())).as("%s gated by its owner", recipe.key()).isSameAs(owner);
        assertThat(registry.getRequiredRecipeLevel(recipe.key())).isGreaterThanOrEqualTo(tool.requiredLevel());
      }
    }
  }

  @Test
  void everyOverchargeHasItsLabelAndSwitch() {
    int declared = 0;
    for (Skill<?> skill : registry.getAllSkills()) {
      for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
        SkillUpgrade<?> skillUpgrade = (SkillUpgrade<?>) upgrade;
        boolean offers = skillUpgrade.getPlayerPreferences().contains(SkillUpgrade.OVERCHARGE);
        assertThat(offers).as("%s switch", upgrade.getId().toString()).isEqualTo(skillUpgrade.hasOvercharge());
        if (skillUpgrade.hasOvercharge()) {
          assertThat(AdaptLanguage.declared(skillUpgrade.getLocalizationKey() + ".overcharge")).isNotNull();
          declared++;
        }
      }
    }
    assertThat(declared).isPositive();
  }
}
