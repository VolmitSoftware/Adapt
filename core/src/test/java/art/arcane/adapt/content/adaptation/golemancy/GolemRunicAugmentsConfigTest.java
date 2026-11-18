package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemWorkSettings;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemRunicAugmentsConfigTest {
  private final GolemRunicAugments.Config config = new GolemRunicAugments.Config();
  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), new ExactIngredientBook());

  @Test
  void theBlankRuneRecipeRingsSmoothStoneWithAmethystAndYieldsTwo() {
    Id shard = material("AMETHYST_SHARD");
    Id stone = material("SMOOTH_STONE");
    ItemRef template = mock(ItemRef.class);
    ItemRef blankRune = mock(ItemRef.class);
    when(template.copyWithAmount(anyInt())).thenReturn(blankRune);
    results.put(Id.of("golemancy", "blank_rune"), template);

    ConfiguredRecipeParser.Result parsed = parser.parse(config.blankRuneRecipe);

    assertThat(parsed.problems()).isEmpty();
    RecipeSpec.Shaped shaped = parsed.draft().key(Id.adapt("test"));
    assertThat(shaped.shape()).containsExactly(" A ", "ASA", " A ");
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(choice(shaped, 'S')).containsExactly(stone);
    assertThat(shaped.result()).isSameAs(blankRune);
    assertThat(config.blankRuneRecipe.getAmount()).isEqualTo(2);
  }

  @Test
  void theLeashHoldsTheWorkRadiusOfTheBiggestTideRune() {
    double largestTideBonus = config.tideRadiusByTier.stream().mapToDouble(Double::doubleValue).max().orElseThrow();

    assertThat(GolemBrainSettings.defaults().leashRadius())
        .isGreaterThanOrEqualTo(GolemWorkSettings.defaults().baseWorkRadius() + largestTideBonus);
  }

  private Id material(String name) {
    Id material = Id.minecraft(name.toLowerCase(Locale.ROOT));
    materials.put(name, material);
    return material;
  }

  private static Set<Id> choice(RecipeSpec.Shaped shaped, char symbol) {
    Ingredient choice = shaped.ingredients().get(symbol);
    assertThat(choice).isInstanceOf(Ingredient.AnyOf.class);
    return ((Ingredient.AnyOf) choice).types();
  }

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Id itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Set<Id> itemTag(Id key) {
      return null;
    }

    @Override
    public ItemRef item(Id type, int amount) {
      return null;
    }

    @Override
    public int maxStackSize(Id type) {
      return 64;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return null;
    }

    @Override
    public ItemRef result(Id id) {
      return results.get(id);
    }
  }
}
