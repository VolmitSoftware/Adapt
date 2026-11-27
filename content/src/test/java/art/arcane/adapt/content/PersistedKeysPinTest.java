package art.arcane.adapt.content;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins every key built-in content writes into saves, configs, permissions, advancements and text: content ids, config
 * paths, permission nodes, advancement and milestone keys, text keys, recipe and item ids. They come from ids, never
 * from class or package names, so moving content between modules or packages must leave the file unchanged. Run with
 * {@code -Dadapt.updatePersistedKeys=true} to rewrite it after an intended change.
 */
class PersistedKeysPinTest {
  private static final Path GOLDEN = Path.of("src/test/resources/persisted-keys.txt");

  @TempDir
  Path dataFolder;

  @Test
  void builtInContentKeepsEveryPersistedKey() throws IOException {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    TreeSet<String> keys = new TreeSet<>();
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      platform.scheduler().advance(200);
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      for (Skill<?> skill : registry.getSkills()) {
        keys.add("skill " + skill.getId());
        keys.add("skill-permission " + ContentIds.usePermission(skill.getId()));
        keys.add("skill-config " + ContentIds.configPath(skill.getId()));
        keys.add("skill-text " + TextKeys.skill(skill.getId()));
        advancements("skill-advancement " + skill.getId() + " ", SkillHost.of(skill).buildAdvancements().toNode(), keys);
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          Id id = adaptation.getId();
          keys.add("adaptation " + id);
          keys.add("adaptation-permission " + ContentIds.usePermission(adaptation.getId()));
          keys.add("adaptation-config " + ContentIds.configPath(adaptation.getId()));
          keys.add("adaptation-text " + AdaptationHost.of(adaptation).textKey());
          keys.add("adaptation-advancement " + id + " " + AdaptationHost.of(adaptation).buildAdvancements().getKey());
          for (RecipeSpec recipe : adaptation.getRecipes()) {
            keys.add("recipe " + id + " " + recipe.key());
          }
          for (BrewingRecipe brew : adaptation.getBrewingRecipes()) {
            keys.add("brewing " + id + " " + brew.id());
          }
          for (CustomItem.Definition<?> item : adaptation.getItems()) {
            keys.add("item " + id + " " + item.id());
          }
        }
      }
      for (CustomItem.Definition<?> item : AdaptApi.content().items()) {
        keys.add("catalog-item " + item.id());
      }
    }
    List<String> lines = new ArrayList<>(keys);
    if (Boolean.getBoolean("adapt.updatePersistedKeys")) {
      Files.write(GOLDEN, lines, StandardCharsets.UTF_8);
    }
    assertThat(lines).as("persisted keys of built-in content; see " + GOLDEN)
        .containsExactlyElementsOf(Files.readAllLines(GOLDEN, StandardCharsets.UTF_8));
  }

  private static void advancements(String prefix, AdvancementNode node, TreeSet<String> keys) {
    keys.add(prefix + node.key());
    for (AdvancementNode child : node.children()) {
      advancements(prefix, child, keys);
    }
  }
}
