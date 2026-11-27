package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.api.TestStubs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeSpecTest {
  private static final Id KEY = Id.of("example", "lantern");
  private static final ItemRef RESULT = TestStubs.stub(ItemRef.class, Map.of());

  @Test
  void shapedRowsErrorNamesTheRecipe() {
    assertThatThrownBy(() -> RecipeSpec.shaped(KEY, List.of(), Map.of(), RESULT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("example:lantern");
  }

  @Test
  void shapelessCountErrorNamesTheRecipe() {
    assertThatThrownBy(() -> RecipeSpec.shapeless(KEY, List.of(), RESULT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("example:lantern");
  }

  @Test
  void emptyAnyOfSaysItHasNoRecipeYet() {
    assertThatThrownBy(() -> new Ingredient.AnyOf(Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no recipe yet");
  }

  @Test
  void factoriesStartAtLevelOneAndKeepEquality() {
    RecipeSpec.Stonecutting cut = RecipeSpec.stonecutting(KEY, Ingredient.of(Id.minecraft("stone")), RESULT);

    assertThat(cut.requiredLevel()).isEqualTo(1);
    assertThat(cut.withRequiredLevel(0).requiredLevel()).isEqualTo(1);
    assertThat(cut.withRequiredLevel(3)).isEqualTo(cut.withRequiredLevel(3)).isNotEqualTo(cut);
  }
}
