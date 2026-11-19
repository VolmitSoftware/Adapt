package art.arcane.adapt.bukkit.feature.recipe;

import art.arcane.adapt.bukkit.natives.IdentityChoice;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.function.Predicate;

/**
 * Paper before 26.x has no predicate recipe choice: matching ingredients are identity choices, and crafting recipes
 * holding one are registered through the natives so the server matches and places them by identity. Recipe changes
 * go through {@link ClientRecipeSync}.
 */
final class BukkitRecipeVersion {
  private BukkitRecipeVersion() {
  }

  static RecipeChoice emptyChoice() {
    return RecipeChoice.empty();
  }

  static RecipeChoice matchingFallback(ItemStack representative, Predicate<ItemStack> matches) {
    return new IdentityChoice(representative, matches);
  }

  static boolean add(Server server, Recipe recipe) {
    return ClientRecipeSync.add(server, recipe);
  }

  static void remove(Server server, NamespacedKey key) {
    ClientRecipeSync.remove(server, key);
  }

  static boolean comparesExactly(RecipeChoice.ExactChoice choice) {
    return !(choice instanceof IdentityChoice);
  }
}
