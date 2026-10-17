package art.arcane.adapt.api.recipe;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExactIngredientBookTest {
  private static final NamespacedKey RECIPE = new NamespacedKey("adapt", "golemancy-guardian-core");

  private final ItemStack core = mock(ItemStack.class);
  private final ItemStack secondCore = mock(ItemStack.class);
  private final ItemStack node = mock(ItemStack.class);
  private final ExactIngredient coreSpec = new ExactIngredient(Material.FIREWORK_STAR, item -> item == core || item == secondCore);
  private final ExactIngredient nodeSpec = new ExactIngredient(Material.BRICK, item -> item == node);
  private final ExactIngredientBook book = new ExactIngredientBook();

  @Test
  void requiredIngredientsAreRecordedPerRecipeAndClearedByAnEmptyList() {
    book.set(RECIPE, List.of(coreSpec, nodeSpec));
    assertThat(book.required(RECIPE)).containsExactly(coreSpec, nodeSpec);

    book.set(RECIPE, List.of());
    assertThat(book.required(RECIPE)).isEmpty();
    assertThat(book.required(null)).isEmpty();
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
    ExactIngredient coreOrNode = new ExactIngredient(Material.FIREWORK_STAR, item -> item == core || item == node);
    ExactIngredient coreOnly = new ExactIngredient(Material.FIREWORK_STAR, item -> item == core);
    book.set(RECIPE, List.of(coreOrNode, coreOnly));

    assertThat(book.satisfiedBy(RECIPE, List.of(core, node))).isTrue();
    assertThat(book.satisfiedBy(RECIPE, List.of(node, node))).isFalse();
  }

  @Test
  void recipesWithoutRequirementsAcceptOnlyAGridWithoutCustomItems() {
    assertThat(book.satisfiedBy(RECIPE, List.of())).isTrue();
    assertThat(book.satisfiedBy(RECIPE, List.of(core))).isFalse();
  }
}
