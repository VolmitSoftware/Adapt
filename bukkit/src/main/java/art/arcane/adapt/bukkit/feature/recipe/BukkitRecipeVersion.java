package art.arcane.adapt.bukkit.feature.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.function.Predicate;

final class BukkitRecipeVersion {
  private BukkitRecipeVersion() {
  }

  static RecipeChoice emptyChoice() {
    return RecipeChoice.empty();
  }

  /** A matching ingredient where the server has no predicate choice: the exact choice of its representative. */
  static RecipeChoice matchingFallback(ItemStack representative, Predicate<ItemStack> matches) {
    return new RecipeChoice.ExactChoice(representative);
  }

  static boolean add(Server server, Recipe recipe) {
    return server.addRecipe(recipe);
  }

  static void remove(Server server, NamespacedKey key) {
    server.removeRecipe(key);
  }

  /** True when an exact choice read back from the server matches by exact item, false when it matches by its own test. */
  static boolean comparesExactly(RecipeChoice.ExactChoice choice) {
    return true;
  }
}
