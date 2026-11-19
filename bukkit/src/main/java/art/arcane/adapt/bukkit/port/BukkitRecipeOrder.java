package art.arcane.adapt.bukkit.port;

import org.bukkit.inventory.Recipe;

import java.util.List;

/** Order in which {@link BukkitMaterialValuePort} hands a material's recipes to the value calculation. */
final class BukkitRecipeOrder {
  private BukkitRecipeOrder() {
  }

  /** The server already lists recipes by recipe type, then by key, on every start. */
  static List<Recipe> canonical(List<Recipe> recipes) {
    return recipes;
  }
}
