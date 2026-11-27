package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExactIngredientBookTest {
  private static final Id RECIPE = Id.adapt("golemancy-guardian-core");

  private final ItemRef core = FakeItem.of("firework_star");
  private final ItemRef secondCore = FakeItem.of("firework_star");
  private final ItemRef node = FakeItem.of("brick");
  private final ExactIngredient coreSpec = new ExactIngredient(Id.minecraft("firework_star"), item -> item == core || item == secondCore);
  private final ExactIngredient nodeSpec = new ExactIngredient(Id.minecraft("brick"), item -> item == node);
  private final ExactIngredientBook book = new ExactIngredientBook();

  @Test
  void requiredIngredientsAreRecordedPerRecipeAndForgottenOnRequest() {
    book.set(RECIPE, List.of(coreSpec, nodeSpec));
    assertThat(book.satisfiedBy(RECIPE, List.of(core, node))).isTrue();

    book.forget(RECIPE);
    assertThat(book.satisfiedBy(RECIPE, List.of(core, node))).isFalse();
  }

  @Test
  void unknownRecipesAreNeverSatisfied() {
    assertThat(book.satisfiedBy(RECIPE, List.of())).isFalse();
    assertThat(book.satisfiedBy(null, List.of())).isFalse();

    book.set(RECIPE, List.of());
    book.forget(RECIPE);
    assertThat(book.satisfiedBy(RECIPE, List.of())).isFalse();
  }

  @Test
  void everyItemMustConsumeOneRequirementAndEveryRequirementMustBeConsumed() {
    book.set(RECIPE, List.of(coreSpec, coreSpec, nodeSpec));

    assertThat(book.satisfiedBy(RECIPE, List.of(node, core, secondCore))).isTrue();
    assertThat(book.satisfiedBy(RECIPE, List.of(core, secondCore))).isFalse();
    assertThat(book.satisfiedBy(RECIPE, List.of(core, secondCore, node, node))).isFalse();
    assertThat(book.satisfiedBy(RECIPE, List.of(core, node, node))).isFalse();
  }

  @Test
  void overlappingRequirementsAreReassignedToFindAFullMatch() {
    ExactIngredient coreOrNode = new ExactIngredient(Id.minecraft("firework_star"), item -> item == core || item == node);
    ExactIngredient coreOnly = new ExactIngredient(Id.minecraft("firework_star"), item -> item == core);
    book.set(RECIPE, List.of(coreOrNode, coreOnly));

    assertThat(book.satisfiedBy(RECIPE, List.of(core, node))).isTrue();
    assertThat(book.satisfiedBy(RECIPE, List.of(node, node))).isFalse();
  }

  @Test
  void recipesWithoutRequirementsAcceptOnlyAGridWithoutCustomItems() {
    book.set(RECIPE, List.of());

    assertThat(book.satisfiedBy(RECIPE, List.of())).isTrue();
    assertThat(book.satisfiedBy(RECIPE, List.of(core))).isFalse();
  }
}
