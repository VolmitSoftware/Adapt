package art.arcane.adapt.api.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface ExactItemResolver {
  ItemStack resolve(NamespacedKey id);
}
