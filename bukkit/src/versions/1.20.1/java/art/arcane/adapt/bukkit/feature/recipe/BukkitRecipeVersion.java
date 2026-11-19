package art.arcane.adapt.bukkit.feature.recipe;

import art.arcane.adapt.bukkit.natives.IdentityChoice;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.function.Predicate;

/**
 * Paper 1.20.1 has no empty recipe choice, so a recipe that needs one cannot be expressed and is skipped. Matching
 * ingredients are identity choices, registered through the natives as on 1.21.1.
 */
final class BukkitRecipeVersion {
  private BukkitRecipeVersion() {
  }

  static RecipeChoice emptyChoice() {
    return null;
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
