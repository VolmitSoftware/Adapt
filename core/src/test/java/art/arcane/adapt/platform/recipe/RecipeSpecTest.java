package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeSpecTest {
  private static final Ingredient STICK = Ingredient.of(Id.minecraft("stick"));
  private static final ItemRef RESULT = FakeItem.of("stone");

  @Test
  void everyKindDefaultsToLevelOne() {
    List<RecipeSpec> recipes = List.of(
        RecipeSpec.shaped(Id.adapt("shaped"), List.of("S"), Map.of('S', STICK), RESULT),
        RecipeSpec.shapeless(Id.adapt("shapeless"), List.of(STICK), RESULT),
        RecipeSpec.cooking(Id.adapt("smoker"), RecipeSpec.CookingStation.SMOKER, STICK, RESULT, 0.1F, 100),
        RecipeSpec.stonecutting(Id.adapt("cutter"), STICK, RESULT),
        RecipeSpec.smithing(Id.adapt("smithing"), null, STICK, STICK, RESULT));

    for (RecipeSpec recipe : recipes) {
      assertThat(recipe.requiredLevel()).as(recipe.key().toString()).isEqualTo(1);
    }
  }

  @Test
  void withRequiredLevelKeepsEverythingElse() {
    List<RecipeSpec> recipes = List.of(
        RecipeSpec.shaped(Id.adapt("shaped"), List.of("S"), Map.of('S', STICK), RESULT),
        RecipeSpec.shapeless(Id.adapt("shapeless"), List.of(STICK), RESULT),
        RecipeSpec.cooking(Id.adapt("blast"), RecipeSpec.CookingStation.BLAST_FURNACE, STICK, RESULT, 0.7F, 100),
        RecipeSpec.stonecutting(Id.adapt("cutter"), STICK, RESULT),
        RecipeSpec.smithing(Id.adapt("smithing"), STICK, STICK, STICK, RESULT));

    for (RecipeSpec recipe : recipes) {
      RecipeSpec leveled = recipe.withRequiredLevel(3);
      assertThat(leveled.requiredLevel()).isEqualTo(3);
      assertThat(leveled.getClass()).isSameAs(recipe.getClass());
      assertThat(leveled.withRequiredLevel(1)).isEqualTo(recipe);
    }
  }

  @Test
  void levelsBelowOneMeanOne() {
    RecipeSpec.Shapeless recipe = RecipeSpec.shapeless(Id.adapt("shapeless"), List.of(STICK), RESULT).withRequiredLevel(0);

    assertThat(recipe.requiredLevel()).isEqualTo(1);
    assertThat(recipe.withRequiredLevel(-4).requiredLevel()).isEqualTo(1);
  }

  @Test
  void cookingKeepsStationExperienceAndTime() {
    RecipeSpec.Cooking campfire = RecipeSpec.cooking(Id.adapt("campfire"), RecipeSpec.CookingStation.CAMPFIRE, STICK, RESULT, 0.35F, 600).withRequiredLevel(2);

    assertThat(campfire.station()).isEqualTo(RecipeSpec.CookingStation.CAMPFIRE);
    assertThat(campfire.experience()).isEqualTo(0.35F);
    assertThat(campfire.cookTicks()).isEqualTo(600);
    assertThat(campfire.requiredLevel()).isEqualTo(2);
  }

  @Test
  void matchingIngredientsNeedABaseAnExampleAndATest() {
    Predicate<ItemRef> any = (ItemRef item) -> true;
    Ingredient.Matching matching = new Ingredient.Matching(Id.minecraft("firework_star"), FakeItem.of("firework_star"), any);

    assertThat(matching.matches()).isSameAs(any);
    assertThatThrownBy(() -> new Ingredient.Matching(null, RESULT, any)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Ingredient.Matching(Id.minecraft("stone"), null, any)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Ingredient.Matching(Id.minecraft("stone"), RESULT, null)).isInstanceOf(NullPointerException.class);
  }
}
