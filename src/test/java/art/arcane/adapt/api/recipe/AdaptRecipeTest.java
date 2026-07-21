package art.arcane.adapt.api.recipe;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdaptRecipeTest {
  @Test
  void everyRecipeTypeExposesItsResultAndDefaultsToLevelOne() {
    ItemStack result = mock(ItemStack.class);
    List<AdaptRecipe> recipes = List.of(
        AdaptRecipe.smoker().result(result).build(),
        AdaptRecipe.furnace().result(result).build(),
        AdaptRecipe.campfire().result(result).build(),
        AdaptRecipe.blast().result(result).build(),
        AdaptRecipe.shapeless().result(result).build(),
        AdaptRecipe.stonecutter().result(result).build(),
        AdaptRecipe.shaped().result(result).build(),
        AdaptRecipe.smithing().result(result).build()
    );

    for (AdaptRecipe recipe : recipes) {
      assertThat(recipe.getResult()).isSameAs(result);
      assertThat(recipe.getRequiredLevel()).isEqualTo(1);
    }
  }

  @Test
  void recipeBuildersRetainExplicitUnlockLevels() {
    AdaptRecipe recipe = AdaptRecipe.shaped()
        .result(mock(ItemStack.class))
        .requiredLevel(4)
        .build();

    assertThat(recipe.getRequiredLevel()).isEqualTo(4);
  }
}
