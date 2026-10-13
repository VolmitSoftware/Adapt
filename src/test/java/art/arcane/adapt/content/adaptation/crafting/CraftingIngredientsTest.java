package art.arcane.adapt.content.adaptation.crafting;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CraftingIngredientsTest {
  @Test
  void shapedRecipesCountEveryOccupiedPosition() {
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    RecipeChoice choice = materialChoice(Material.DIAMOND);
    when(recipe.getShape()).thenReturn(new String[]{"AAA", "AAA", "AAA"});
    when(recipe.getChoiceMap()).thenReturn(Map.of('A', choice));

    List<RecipeChoice> choices = CraftingIngredients.perCraftChoices(recipe);
    assertThat(choices).hasSize(9);
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(choices, new ItemStack[]{stack(Material.DIAMOND, 18)}, 4, 0);
    assertThat(plan.crafts()).isEqualTo(2);
    assertThat(plan.costs()).containsExactly(18);
  }

  @Test
  void exactIngredientsRejectPlainMaterialSubstitutes() {
    RecipeChoice.ExactChoice choice = mock(RecipeChoice.ExactChoice.class);
    ItemStack plain = stack(Material.DIAMOND, 8);
    ItemStack marked = stack(Material.DIAMOND, 2);
    when(choice.test(marked)).thenReturn(true);

    CraftingIngredients.BatchPlan rejected = CraftingIngredients.plan(List.of(choice), new ItemStack[]{plain}, 8, 0);
    assertThat(rejected.crafts()).isZero();
    assertThat(rejected.costs()).containsExactly(0);
    CraftingIngredients.BatchPlan accepted = CraftingIngredients.plan(List.of(choice), new ItemStack[]{plain, marked}, 8, 0);
    assertThat(accepted.crafts()).isEqualTo(2);
    assertThat(accepted.costs()).containsExactly(0, 2);
  }

  @Test
  void exactChoicesAreReservedBeforeBroadMaterialChoices() {
    RecipeChoice.ExactChoice exact = mock(RecipeChoice.ExactChoice.class);
    ItemStack marked = stack(Material.DIAMOND, 1);
    ItemStack plain = stack(Material.DIAMOND, 1);
    when(exact.test(marked)).thenReturn(true);
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(
        List.of(materialChoice(Material.DIAMOND), exact), new ItemStack[]{marked, plain}, 1, 0);

    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void broadAlternativesDoNotConsumeTheOnlyNarrowIngredient() {
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(
        List.of(materialChoice(Material.DIAMOND, Material.EMERALD), materialChoice(Material.DIAMOND)),
        new ItemStack[]{stack(Material.DIAMOND, 1), stack(Material.EMERALD, 1)}, 1, 0);

    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void overlappingExactAlternativesReassignScarceItems() {
    ItemStack scarce = stack(Material.DIAMOND, 1);
    ItemStack alternative = stack(Material.DIAMOND, 1);
    RecipeChoice.ExactChoice broad = mock(RecipeChoice.ExactChoice.class);
    RecipeChoice.ExactChoice narrow = mock(RecipeChoice.ExactChoice.class);
    when(broad.test(scarce)).thenReturn(true);
    when(broad.test(alternative)).thenReturn(true);
    when(narrow.test(scarce)).thenReturn(true);

    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(List.of(broad, narrow), new ItemStack[]{scarce, alternative}, 1, 0);
    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void incompleteNextCraftRollsBackItsPartialCost() {
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(
        List.of(materialChoice(Material.DIAMOND), materialChoice(Material.STICK)),
        new ItemStack[]{stack(Material.DIAMOND, 2), stack(Material.STICK, 1)}, 3, 0);

    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void alternateMaterialsAndReserveAreHonored() {
    RecipeChoice choice = materialChoice(Material.OAK_PLANKS, Material.BIRCH_PLANKS);
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(List.of(choice),
        new ItemStack[]{null, stack(Material.BIRCH_PLANKS, 12)}, 16, 8);

    assertThat(plan.crafts()).isEqualTo(4);
    assertThat(plan.costs()).containsExactly(0, 4);
  }

  @Test
  void broadRecipesKeepNamedIngredientsUnlessExplicitlyRequired() {
    ItemStack marked = stack(Material.DIAMOND, 3);
    when(marked.hasItemMeta()).thenReturn(true);
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(List.of(materialChoice(Material.DIAMOND)),
        new ItemStack[]{marked, stack(Material.DIAMOND, 2)}, 4, 0);

    assertThat(plan.crafts()).isEqualTo(2);
    assertThat(plan.costs()).containsExactly(0, 2);
  }

  @Test
  void emptyAndZeroSizedBatchesSpendNothing() {
    assertThat(CraftingIngredients.plan(List.of(), new ItemStack[]{stack(Material.DIAMOND, 4)}, 5, 0).crafts()).isZero();
    assertThat(CraftingIngredients.plan(List.of(materialChoice(Material.DIAMOND)),
        new ItemStack[]{stack(Material.DIAMOND, 4)}, 0, 0).crafts()).isZero();
  }

  private static RecipeChoice.MaterialChoice materialChoice(Material... materials) {
    RecipeChoice.MaterialChoice choice = mock(RecipeChoice.MaterialChoice.class);
    when(choice.test(any(ItemStack.class))).thenAnswer(invocation -> {
      ItemStack item = invocation.getArgument(0);
      for (Material material : materials) {
        if (item.getType() == material) {
          return true;
        }
      }
      return false;
    });
    return choice;
  }

  private static ItemStack stack(Material material, int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(material);
    when(stack.getAmount()).thenReturn(amount);
    return stack;
  }
}
