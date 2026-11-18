package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CraftingIngredientsTest {
  private static final Id DIAMOND = Id.minecraft("diamond");
  private static final Id EMERALD = Id.minecraft("emerald");
  private static final Id STICK = Id.minecraft("stick");
  private static final Id OAK_PLANKS = Id.minecraft("oak_planks");
  private static final Id BIRCH_PLANKS = Id.minecraft("birch_planks");

  @TempDir
  Path dataDirectory;

  @BeforeEach
  void installPlatform() {
    TestCores.install(TestPlatform.builder(dataDirectory).build().platform());
  }

  @AfterEach
  void clearPlatform() {
    TestCores.clear();
  }

  @Test
  void shapedRecipesCountEveryOccupiedPosition() {
    RecipeChoice choice = materialChoice(DIAMOND);
    RecipeView recipe = new RecipeView(Id.minecraft("diamond_block"), RecipeView.Kind.SHAPED,
        List.of("AAA", "AAA", "AAA"), Map.of('A', choice), List.of(), new FakeItem(Id.minecraft("diamond_block"), 1));

    List<RecipeChoice> choices = CraftingIngredients.perCraftChoices(recipe);
    assertThat(choices).hasSize(9);
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(choices, new ItemRef[]{stack(DIAMOND, 18)}, 4, 0);
    assertThat(plan.crafts()).isEqualTo(2);
    assertThat(plan.costs()).containsExactly(18);
  }

  @Test
  void exactIngredientsRejectPlainMaterialSubstitutes() {
    ItemRef plain = stack(DIAMOND, 8);
    ItemRef marked = marked(DIAMOND, 2, "marked");
    RecipeChoice choice = RecipeChoice.ofExact(List.of(marked(DIAMOND, 1, "marked")));

    CraftingIngredients.BatchPlan rejected = CraftingIngredients.plan(List.of(choice), new ItemRef[]{plain}, 8, 0);
    assertThat(rejected.crafts()).isZero();
    assertThat(rejected.costs()).containsExactly(0);
    CraftingIngredients.BatchPlan accepted = CraftingIngredients.plan(List.of(choice), new ItemRef[]{plain, marked}, 8, 0);
    assertThat(accepted.crafts()).isEqualTo(2);
    assertThat(accepted.costs()).containsExactly(0, 2);
  }

  @Test
  void exactChoicesAreReservedBeforeBroadMaterialChoices() {
    ItemRef marked = marked(DIAMOND, 1, "marked");
    ItemRef plain = stack(DIAMOND, 1);
    RecipeChoice exact = RecipeChoice.ofExact(List.of(marked(DIAMOND, 1, "marked")));
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(
        List.of(materialChoice(DIAMOND), exact), new ItemRef[]{marked, plain}, 1, 0);

    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void broadAlternativesDoNotConsumeTheOnlyNarrowIngredient() {
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(
        List.of(materialChoice(DIAMOND, EMERALD), materialChoice(DIAMOND)),
        new ItemRef[]{stack(DIAMOND, 1), stack(EMERALD, 1)}, 1, 0);

    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void overlappingExactAlternativesReassignScarceItems() {
    ItemRef scarce = marked(DIAMOND, 1, "scarce");
    ItemRef alternative = marked(DIAMOND, 1, "alternative");
    RecipeChoice broad = RecipeChoice.ofExact(List.of(marked(DIAMOND, 1, "scarce"), marked(DIAMOND, 1, "alternative")));
    RecipeChoice narrow = RecipeChoice.ofExact(List.of(marked(DIAMOND, 1, "scarce")));

    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(List.of(broad, narrow), new ItemRef[]{scarce, alternative}, 1, 0);
    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void incompleteNextCraftRollsBackItsPartialCost() {
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(
        List.of(materialChoice(DIAMOND), materialChoice(STICK)),
        new ItemRef[]{stack(DIAMOND, 2), stack(STICK, 1)}, 3, 0);

    assertThat(plan.crafts()).isEqualTo(1);
    assertThat(plan.costs()).containsExactly(1, 1);
  }

  @Test
  void alternateMaterialsAndReserveAreHonored() {
    RecipeChoice choice = materialChoice(OAK_PLANKS, BIRCH_PLANKS);
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(List.of(choice),
        new ItemRef[]{null, stack(BIRCH_PLANKS, 12)}, 16, 8);

    assertThat(plan.crafts()).isEqualTo(4);
    assertThat(plan.costs()).containsExactly(0, 4);
  }

  @Test
  void broadRecipesKeepNamedIngredientsUnlessExplicitlyRequired() {
    ItemRef marked = marked(DIAMOND, 3, "named");
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(List.of(materialChoice(DIAMOND)),
        new ItemRef[]{marked, stack(DIAMOND, 2)}, 4, 0);

    assertThat(plan.crafts()).isEqualTo(2);
    assertThat(plan.costs()).containsExactly(0, 2);
  }

  @Test
  void emptyAndZeroSizedBatchesSpendNothing() {
    assertThat(CraftingIngredients.plan(List.of(), new ItemRef[]{stack(DIAMOND, 4)}, 5, 0).crafts()).isZero();
    assertThat(CraftingIngredients.plan(List.of(materialChoice(DIAMOND)),
        new ItemRef[]{stack(DIAMOND, 4)}, 0, 0).crafts()).isZero();
  }

  private static RecipeChoice materialChoice(Id... materials) {
    return RecipeChoice.ofTypes(Arrays.asList(materials));
  }

  private static ItemRef stack(Id material, int amount) {
    return new FakeItem(material, amount);
  }

  private static ItemRef marked(Id material, int amount, String name) {
    FakeItem item = new FakeItem(material, amount);
    item.setDisplayName(name);
    return item;
  }
}
