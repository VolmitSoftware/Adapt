package art.arcane.adapt.bukkit.port;

import org.bukkit.Keyed;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Paper 1.20.1 keeps recipes per recipe type in a map keyed by the type object, so the type order of
 * {@code getRecipesFor} changes from one server start to the next. The material value calculation cuts recipe cycles
 * in visiting order, so the order is fixed here to the one later servers use: crafting, smelting, blasting, smoking,
 * campfire, stonecutting, smithing, then by key.
 */
final class BukkitRecipeOrder {
  private static final Comparator<Recipe> ORDER = Comparator.comparingInt(BukkitRecipeOrder::typeRank)
      .thenComparing(BukkitRecipeOrder::key);

  private BukkitRecipeOrder() {
  }

  static List<Recipe> canonical(List<Recipe> recipes) {
    List<Recipe> ordered = new ArrayList<>(recipes);
    ordered.sort(ORDER);
    return ordered;
  }

  private static int typeRank(Recipe recipe) {
    if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
      return 0;
    }
    if (recipe instanceof BlastingRecipe) {
      return 2;
    }
    if (recipe instanceof SmokingRecipe) {
      return 3;
    }
    if (recipe instanceof CampfireRecipe) {
      return 4;
    }
    if (recipe instanceof FurnaceRecipe) {
      return 1;
    }
    if (recipe instanceof StonecuttingRecipe) {
      return 5;
    }
    if (recipe instanceof SmithingRecipe) {
      return 6;
    }
    return 7;
  }

  private static String key(Recipe recipe) {
    return recipe instanceof Keyed keyed ? keyed.getKey().toString() : "";
  }
}
