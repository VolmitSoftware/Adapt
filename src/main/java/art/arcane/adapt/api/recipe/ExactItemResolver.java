package art.arcane.adapt.api.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public interface ExactItemResolver {
  ExactIngredient ingredient(NamespacedKey id);

  ItemStack result(NamespacedKey id);
}
