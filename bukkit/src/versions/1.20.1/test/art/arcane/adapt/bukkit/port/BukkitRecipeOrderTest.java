package art.arcane.adapt.bukkit.port;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The iron ingot recipes as Paper 1.20.1 may list them after a start that iterated blasting first. */
class BukkitRecipeOrderTest {
  @Test
  void recipesAreOrderedByTypeThenKeyWhateverTheServerOrder() {
    Recipe blastingRaw = recipe(BlastingRecipe.class, "iron_ingot_from_blasting_raw_iron");
    Recipe blastingOre = recipe(BlastingRecipe.class, "iron_ingot_from_blasting_iron_ore");
    Recipe nuggets = recipe(ShapedRecipe.class, "iron_ingot_from_nuggets");
    Recipe block = recipe(ShapelessRecipe.class, "iron_ingot_from_iron_block");
    Recipe smeltingRaw = recipe(FurnaceRecipe.class, "iron_ingot_from_smelting_raw_iron");
    Recipe smeltingOre = recipe(FurnaceRecipe.class, "iron_ingot_from_smelting_iron_ore");

    List<Recipe> canonical = List.of(block, nuggets, smeltingOre, smeltingRaw, blastingOre, blastingRaw);

    assertThat(BukkitRecipeOrder.canonical(List.of(blastingRaw, blastingOre, nuggets, block, smeltingRaw, smeltingOre)))
        .containsExactlyElementsOf(canonical);
    assertThat(BukkitRecipeOrder.canonical(List.of(smeltingOre, block, blastingRaw, nuggets, smeltingRaw, blastingOre)))
        .containsExactlyElementsOf(canonical);
  }

  private static <T extends Recipe> Recipe recipe(Class<T> type, String key) {
    T recipe = mock(type);
    if (recipe instanceof Keyed keyed) {
      when(keyed.getKey()).thenReturn(NamespacedKey.minecraft(key));
    }
    return recipe;
  }
}
