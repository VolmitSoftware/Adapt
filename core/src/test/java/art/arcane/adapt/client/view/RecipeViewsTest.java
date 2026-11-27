package art.arcane.adapt.client.view;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every adaptation that registers recipes lists each of them in the view at the level that unlocks it, with item refs
 * the client can draw; an adaptation without recipes lists none.
 */
class RecipeViewsTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static RegistryLookup registries;

  @BeforeAll
  static void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
    registries = AdaptCore.platform().host().registries();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  private static Adaptation<?> adaptation(String name) {
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        if (adaptation.getId().toString().equals(name)) {
          return adaptation;
        }
      }
    }
    throw new AssertionError(name);
  }

  @Test
  void everyRecipeOfEveryAdaptationIsListedAtItsLevel() {
    Map<String, List<RecipeView>> pages = new HashMap<>();
    for (RecipeView view : RecipeViews.build(registry, UUID.randomUUID())) {
      pages.computeIfAbsent(view.adaptationId(), (String id) -> new ArrayList<>()).add(view);
    }
    int unlocking = 0;
    int total = 0;
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        List<RecipeView> views = RecipeViews.of(adaptation, registries);
        String where = adaptation.getId().toString();
        List<Integer> levels = new ArrayList<>();
        for (RecipeSpec recipe : adaptation.getRecipes()) {
          levels.add(recipe.requiredLevel());
        }
        for (int index = 0; index < adaptation.getBrewingRecipes().size(); index++) {
          levels.add(1);
        }
        List<Integer> crafted = new ArrayList<>();
        for (RecipeView view : views) {
          if (view.kind() != RecipeView.Kind.MIXING) {
            crafted.add(view.requiredLevel());
          }
          assertThat(view.adaptationId()).as(where).isEqualTo(ToolViews.page(adaptation));
          assertThat(view.result().empty()).as(where).isFalse();
          assertThat(view.inputs()).as(where).isNotEmpty();
          assertThat(view.requiredLevel()).as(where).isBetween(1, Math.max(1, adaptation.getMaxLevel()));
        }
        assertThat(crafted).as(where).containsExactlyInAnyOrderElementsOf(levels);
        assertThat(views.size() > levels.size()).as(where).isEqualTo(!adaptation.getMixingRecipes().isEmpty());
        if (!views.isEmpty()) {
          unlocking++;
          total += views.size();
          assertThat(pages.get(ToolViews.page(adaptation))).as(where).containsAll(views);
        }
      }
    }
    assertThat(unlocking).isGreaterThanOrEqualTo(60);
    assertThat(total).isGreaterThan(200);
    assertThat(RecipeViews.of(adaptation("adapt:agility/wall_jump"), registries)).isEmpty();
  }

  @Test
  void apothecaryListsItsBrewsInPairsThenItsElixirThenAMixPerBottleCount() {
    List<RecipeView> views = RecipeViews.of(adaptation("adapt:brewing/upgrade/apothecary"), registries);
    assertThat(views).hasSize(13);
    assertThat(views.subList(0, 11)).extracting(RecipeView::kind).containsOnly(RecipeView.Kind.BREWING);
    assertThat(views.get(0).group()).isEqualTo(views.get(1).group()).isEqualTo("minecraft:absorption");
    assertThat(views.get(10).group()).contains(",");
    RecipeView first = views.get(0);
    assertThat(first.inputs().get(0).nameKey()).isEqualTo("item.minecraft.potion.effect.healing");
    assertThat(first.inputs().get(0).icon().spriteId())
        .isEqualTo("minecraft:textures/item/potion_overlay.png#f82423|minecraft:textures/item/potion.png");
    assertThat(first.inputs().get(1).icon().itemId()).isEqualTo("minecraft:quartz");
    RecipeView.Item result = first.result();
    assertThat(result.name()).isEqualTo("Bottled Absorption");
    assertThat(result.icon().spriteId()).startsWith("minecraft:textures/item/potion_overlay.png#808080|");
    assertThat(result.effects()).containsExactly(new RecipeView.Effect("minecraft:absorption", 0, 1200, false));
    RecipeView two = views.get(11);
    RecipeView three = views.get(12);
    assertThat(two.kind()).isEqualTo(RecipeView.Kind.MIXING);
    assertThat(two.requiredLevel()).isEqualTo(1);
    assertThat(two.inputs()).hasSize(3);
    assertThat(two.inputs().get(2).icon().itemId()).isEqualTo("minecraft:glowstone_dust");
    assertThat(three.requiredLevel()).isEqualTo(3);
    assertThat(three.inputs()).hasSize(4);
    assertThat(three.result().name()).isNotEmpty();
  }

  @Test
  void toolsDrawWithTheirOwnArtAndLooksMatchTheirRecipes() {
    for (MutationTool<?> tool : MutationTools.all(registry)) {
      List<RecipeView> views = RecipeViews.of(tool.owner(), registries);
      for (RecipeSpec recipe : tool.recipes()) {
        assertThat(MutationTools.ofRecipe(recipe.key())).as(recipe.key().toString()).isSameAs(tool);
      }
      assertThat(views).extracting(RecipeView::result).extracting(RecipeView.Item::icon).as(tool.item().id().toString()).contains(ToolViews.icon(tool));
    }
    assertThat(RecipeViews.look("architect-chalk-polyline")).isEqualTo("chalk-polyline");
    assertThat(RecipeViews.look("elevator")).isEmpty();
  }

  @Test
  void shapedRecipesKeepTheirGridAndCountTheirResult() {
    RecipeView elevator = RecipeViews.of(adaptation("adapt:architect/elevator"), registries).get(0);
    assertThat(elevator.kind()).isEqualTo(RecipeView.Kind.SHAPED);
    assertThat(elevator.width()).isEqualTo(3);
    assertThat(elevator.inputs()).hasSize(9);
    assertThat(elevator.inputs().get(4).icon().itemId()).isEqualTo("minecraft:ender_pearl");
    RecipeView swap = RecipeViews.of(adaptation("adapt:axe/logswap"), registries).get(0);
    assertThat(swap.kind()).isEqualTo(RecipeView.Kind.SHAPELESS);
    assertThat(swap.result().count()).isEqualTo(8);
    RecipeView leather = RecipeViews.of(adaptation("adapt:crafting/leather"), registries).get(0);
    assertThat(leather.kind()).isEqualTo(RecipeView.Kind.COOKING);
    assertThat(leather.inputs()).hasSize(1);
  }
}
